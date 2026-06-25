package com.bankingswitch.orchestrator.service;

import com.bankingswitch.orchestrator.model.*;
import com.bankingswitch.orchestrator.model.entity.TransactionEntity;
import com.bankingswitch.orchestrator.producer.CbsRequestProducer;
import com.bankingswitch.orchestrator.producer.NpciResponseProducer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestratorService {

    private final TransactionStateService stateService;
    private final CbsRequestProducer cbsRequestProducer;
    private final NpciResponseProducer npciResponseProducer;
    private final XmlMapper xmlMapper = new XmlMapper();

    public void processInboundEvent(InboundTransactionEvent event) {
        log.info("Processing inbound event: {}", event.getTxnId());
        TransactionEntity txn = stateService.createTransaction(event.getTxnId(), event.getTxnType(), event.getXmlPayload());
        
        try {
            JsonNode rootNode = xmlMapper.readTree(event.getXmlPayload().getBytes());
            
            if ("ReqBalEnq".equals(event.getTxnType())) {
                String vpa = rootNode.path("Txn").path("Payer").path("addr").asText();
                stateService.updateTransactionState(txn.getTxnId(), TransactionState.CBS_PENDING, "Sending balance inquiry to CBS");
                
                CbsRequestEvent req = CbsRequestEvent.builder()
                        .txnId(txn.getTxnId())
                        .operation("BALANCE")
                        .vpa(vpa)
                        .xmlPayload(event.getXmlPayload())
                        .build();
                cbsRequestProducer.sendRequest(req);
                
            } else if ("ReqPay".equals(event.getTxnType())) {
                // A ReqPay directed at the banking switch means we are the Payer's bank, so we must DEBIT
                String txnType = "DEBIT"; 
                String vpa = rootNode.path("Txn").path("Payer").path("addr").asText();
                Double amount = rootNode.path("Txn").path("Payer").path("Amount").path("value").asDouble();
                
                stateService.updateTransactionState(txn.getTxnId(), TransactionState.CBS_PENDING, "Sending " + txnType + " to CBS");
                
                CbsRequestEvent req = CbsRequestEvent.builder()
                        .txnId(txn.getTxnId())
                        .operation(txnType)
                        .vpa(vpa)
                        .amount(amount)
                        .xmlPayload(event.getXmlPayload())
                        .build();
                cbsRequestProducer.sendRequest(req);
            } else if ("ReqCredit".equals(event.getTxnType())) {
                // A ReqCredit directed at the banking switch means we are the Payee's bank, so we must CREDIT
                String txnType = "CREDIT"; 
                String vpa = rootNode.path("Txn").path("Payee").path("addr").asText();
                Double amount = rootNode.path("Txn").path("Payee").path("Amount").path("value").asDouble();
                
                stateService.updateTransactionState(txn.getTxnId(), TransactionState.CBS_PENDING, "Sending " + txnType + " to CBS");
                
                CbsRequestEvent req = CbsRequestEvent.builder()
                        .txnId(txn.getTxnId())
                        .operation(txnType)
                        .vpa(vpa)
                        .amount(amount)
                        .xmlPayload(event.getXmlPayload())
                        .build();
                cbsRequestProducer.sendRequest(req);
            }
        } catch (Exception e) {
            log.error("Failed to parse XML", e);
            stateService.updateTransactionState(txn.getTxnId(), TransactionState.FAILED, "XML Parse Error");
        }
    }

    public void processCbsResponse(CbsResponseEvent event) {
        log.info("Processing CBS response: {}", event.getTxnId());
        TransactionEntity txn = stateService.getTransaction(event.getTxnId());
        if (txn == null) return;
        
        TransactionState newState = "SUCCESS".equals(event.getStatus()) ? TransactionState.CBS_SUCCESS : TransactionState.CBS_FAILED;
        stateService.updateTransactionState(event.getTxnId(), newState, "Received CBS response: " + event.getStatus());
        
        TransactionState finalState = "SUCCESS".equals(event.getStatus()) ? TransactionState.SUCCESS : TransactionState.FAILED;
        stateService.updateTransactionState(event.getTxnId(), finalState, "Transaction completed");

        String respTxnType;
        if (txn.getTxnType().equals("ReqBalEnq")) {
            respTxnType = "RespBalEnq";
        } else if (txn.getTxnType().equals("ReqCredit")) {
            respTxnType = "RespCredit";
        } else {
            respTxnType = "RespPay";
        }

        NpciResponseEvent npciResp = NpciResponseEvent.builder()
                .txnId(event.getTxnId())
                .txnType(respTxnType)
                .status(event.getStatus())
                .errorCode(event.getErrorCode())
                .balance(event.getBalance())
                .xmlPayload(txn.getXmlPayload())
                .build();
        
        npciResponseProducer.sendResponse(npciResp);
    }
}
