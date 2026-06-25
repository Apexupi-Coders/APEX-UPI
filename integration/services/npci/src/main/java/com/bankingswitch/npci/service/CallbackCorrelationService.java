package com.bankingswitch.npci.service;

import com.bankingswitch.npci.model.dto.UpiResponse;
import com.bankingswitch.npci.model.entity.BankEndpoint;
import com.bankingswitch.npci.model.entity.NpciTransactionLog;
import com.bankingswitch.npci.model.entity.VpaRegistryEntry;
import com.bankingswitch.npci.repository.BankEndpointRepository;
import com.bankingswitch.npci.repository.TransactionLogRepository;
import com.bankingswitch.npci.repository.VpaRegistryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.time.Instant;

@Service
public class CallbackCorrelationService {

    private final RestTemplate restTemplate;
    private final TransactionLogRepository transactionLogRepository;
    private final NpciXmlService npciXmlService;
    private final VpaRegistryRepository vpaRegistryRepository;
    private final BankEndpointRepository bankEndpointRepository;

    @Value("${app.psp-callback-url}")
    private String pspCallbackUrl;
    
    @Value("${app.bankswitch-host}")
    private String defaultBankswitchHost;

    public CallbackCorrelationService(RestTemplate restTemplate,
                                      TransactionLogRepository transactionLogRepository,
                                      NpciXmlService npciXmlService,
                                      VpaRegistryRepository vpaRegistryRepository,
                                      BankEndpointRepository bankEndpointRepository) {
        this.restTemplate = restTemplate;
        this.transactionLogRepository = transactionLogRepository;
        this.npciXmlService = npciXmlService;
        this.vpaRegistryRepository = vpaRegistryRepository;
        this.bankEndpointRepository = bankEndpointRepository;
    }

    public String processCallback(String txnId, String rawXml) {
        UpiResponse response = npciXmlService.parseResponse(rawXml);
        String type = response.getTxn() != null ? response.getTxn().getType() : "UNKNOWN";
        String status = response.getResp() != null ? response.getResp().getResult() : "COMPLETED";

        Optional<NpciTransactionLog> logOpt = transactionLogRepository.findById(txnId);
        if (logOpt.isPresent()) {
            NpciTransactionLog log = logOpt.get();
            log.setResponseXml(rawXml);
            
            if ("RespPay".equalsIgnoreCase(type) && "SUCCESS".equalsIgnoreCase(status)) {
                log.setStatus("CREDIT_PENDING");
                transactionLogRepository.save(log);
                
                // Initiate Credit Leg!
                initiateCreditLeg(log);
                return npciXmlService.buildAck(txnId, "Ack");
            } else if ("RespCredit".equalsIgnoreCase(type)) {
                log.setStatus(status);
                transactionLogRepository.save(log);
                
                // Forward the final RespPay to PSP
                forwardFinalRespPayToPsp(txnId, status);
                return npciXmlService.buildAck(txnId, "Ack");
            } else {
                log.setStatus(status);
                transactionLogRepository.save(log);
            }
        }

        // Default behavior for other callbacks (like RespBalEnq or FAILED RespPay)
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            HttpEntity<String> entity = new HttpEntity<>(rawXml, headers);
            
            String path;
            if ("PAY".equalsIgnoreCase(type) || "RespPay".equalsIgnoreCase(type)) {
                path = "/npci/callback/resp-pay/" + txnId;
            } else if ("BALANCEENQUIRY".equalsIgnoreCase(type) || "BALANCE".equalsIgnoreCase(type) || "RespBalEnq".equalsIgnoreCase(type)) {
                path = "/npci/callback/resp-bal-enq/" + txnId;
            } else {
                path = "/upi/callback/" + type + "/" + txnId;
            }
            String targetUrl = pspCallbackUrl + path;
            System.out.println("[NPCI-SIMULATOR] Forwarding final callback to PSP at: " + targetUrl);
            ResponseEntity<String> pspResponse = restTemplate.postForEntity(targetUrl, entity, String.class);
            System.out.println("[NPCI-SIMULATOR] PSP received callback with status: " + pspResponse.getStatusCode());
        } catch (Exception e) {
            System.err.println("Failed to forward callback to PSP: " + e.getMessage());
        }

        return npciXmlService.buildAck(txnId, "Ack");
    }

    private void initiateCreditLeg(NpciTransactionLog log) {
        String targetVpa = log.getPayeeVpa();
        if (targetVpa == null) return;

        Optional<VpaRegistryEntry> vpaEntry = vpaRegistryRepository.findById(targetVpa);
        String switchUrl = defaultBankswitchHost;
        if (vpaEntry.isPresent()) {
            Optional<BankEndpoint> endpoint = bankEndpointRepository.findById(vpaEntry.get().getBankCode());
            if (endpoint.isPresent()) {
                switchUrl = endpoint.get().getSwitchBaseUrl();
            }
        }
        String targetUrl = switchUrl + "/bank/upi/ReqCredit";

        // Generate ReqCredit XML
        String reqCreditXml = String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<ReqCredit>\n" +
            "  <Head msgId=\"msg-%s\" orgId=\"NPCI\"/>\n" +
            "  <Txn id=\"%s\" type=\"CREDIT\" ts=\"%s\">\n" +
            "    <Payee addr=\"%s\">\n" +
            "      <Amount value=\"%s\" curr=\"INR\"/>\n" +
            "    </Payee>\n" +
            "  </Txn>\n" +
            "</ReqCredit>", 
            log.getTxnId(), log.getTxnId(), Instant.now().toString(), targetVpa, log.getAmount());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        HttpEntity<String> entity = new HttpEntity<>(reqCreditXml, headers);

        try {
            restTemplate.postForEntity(targetUrl, entity, String.class);
        } catch (Exception e) {
            System.err.println("Failed to route ReqCredit to Payee Bank: " + e.getMessage());
            // If credit fails to route, we should technically reverse the debit, but this is a mock.
            forwardFinalRespPayToPsp(log.getTxnId(), "FAILED");
        }
    }

    private void forwardFinalRespPayToPsp(String txnId, String status) {
        String respPayXml = String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<RespPay>\n" +
            "  <Head msgId=\"msg-resp-%s\" orgId=\"NPCI\"/>\n" +
            "  <Txn id=\"%s\" type=\"PAY\" ts=\"%s\"/>\n" +
            "  <Resp result=\"%s\" errCode=\"\"/>\n" +
            "</RespPay>", 
            txnId, txnId, Instant.now().toString(), status);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        HttpEntity<String> entity = new HttpEntity<>(respPayXml, headers);

        try {
            String targetUrl = pspCallbackUrl + "/npci/callback/resp-pay/" + txnId;
            restTemplate.postForEntity(targetUrl, entity, String.class);
        } catch (Exception e) {
            System.err.println("Failed to forward final RespPay to PSP: " + e.getMessage());
        }
    }
}
