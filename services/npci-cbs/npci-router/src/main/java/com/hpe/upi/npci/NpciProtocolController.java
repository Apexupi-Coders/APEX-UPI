package com.hpe.upi.npci;

import com.hpe.upi.npci.adapter.*;
import com.hpe.upi.npci.registry.VpaRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NpciProtocolController — exposes ISO 8583 and NPCI XML for inspection.
 *
 * This controller lets the sales demo team see exactly what protocol
 * messages are generated for any given transaction — without needing
 * to read the logs.
 *
 * Endpoints:
 *   POST /api/npci/protocol/preview   — generate ISO 8583 + XML for a sample txn
 *   GET  /api/npci/protocol/iso8583/mti  — explain all MTI codes
 *   GET  /api/npci/protocol/iso8583/response-codes — explain all response codes
 *   GET  /api/npci/protocol/xml/sample  — show a sample NPCI XML message
 */
@RestController
@RequestMapping("/api/npci/protocol")
@CrossOrigin(origins = "*")
public class NpciProtocolController {

    private final NpciXmlMapper  xmlMapper;
    private final Iso8583Codec   iso8583Codec;
    private final VpaRegistry    vpaRegistry;

    public NpciProtocolController(NpciXmlMapper xmlMapper,
                                   Iso8583Codec iso8583Codec,
                                   VpaRegistry vpaRegistry) {
        this.xmlMapper    = xmlMapper;
        this.iso8583Codec = iso8583Codec;
        this.vpaRegistry  = vpaRegistry;
    }

    /**
     * Preview the ISO 8583 message and NPCI XML that would be generated
     * for a given transaction — without actually routing it.
     *
     * Example:
     *   POST /api/npci/protocol/preview
     *   {"payerVpa":"alice@sbi","payeeVpa":"bob@hdfc","amount":"500","operation":"DEBIT"}
     */
    @PostMapping("/preview")
    public ResponseEntity<Object> previewProtocol(@RequestBody Map<String, String> req) {
        String payerVpa  = req.getOrDefault("payerVpa", "alice@sbi");
        String payeeVpa  = req.getOrDefault("payeeVpa", "bob@hdfc");
        String amountStr = req.getOrDefault("amount", "500");
        String opStr     = req.getOrDefault("operation", "DEBIT");

        NpciRequest.OperationType operation;
        try {
            operation = NpciRequest.OperationType.valueOf(opStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid operation. Use: DEBIT, CREDIT, REVERSAL"));
        }

        // Build NpciRequest manually (bypassing routing — preview only)
        NpciRequest npciReq = new NpciRequest();
        npciReq.setMsgId("MSG" + UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase());
        npciReq.setTxnId("TXN" + UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase());
        npciReq.setRrn("RRN" + UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase());
        npciReq.setPayerVpa(payerVpa);
        npciReq.setPayeeVpa(payeeVpa);
        npciReq.setPayerBank(payerVpa.contains("@") ? payerVpa.split("@")[1].toUpperCase() : "UNKNOWN");
        npciReq.setPayeeBank(payeeVpa.contains("@") ? payeeVpa.split("@")[1].toUpperCase() : "UNKNOWN");
        npciReq.setAmount(new BigDecimal(amountStr));
        npciReq.setOperation(operation);

        // Resolve accounts from registry
        VpaRegistry.VpaRecord payerRec = vpaRegistry.lookup(payerVpa);
        VpaRegistry.VpaRecord payeeRec = vpaRegistry.lookup(payeeVpa);
        if (payerRec != null) {
            npciReq.setPayerAccountNumber(payerRec.accountNumber);
            npciReq.setNpciRoutingCode(payerRec.ifsc);
        }
        if (payeeRec != null) {
            npciReq.setPayeeAccountNumber(payeeRec.accountNumber);
        }
        if (npciReq.getNpciRoutingCode() == null) npciReq.setNpciRoutingCode("UNKN0001");

        // Generate ISO 8583
        Iso8583Codec.Iso8583Message iso = iso8583Codec.encode(npciReq);

        // Generate NPCI XML
        String xml = xmlMapper.toXml(npciReq);

        // Build response
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("txnId",     npciReq.getTxnId());
        result.put("msgId",     npciReq.getMsgId());
        result.put("operation", operation.name());
        result.put("payerVpa",  payerVpa);
        result.put("payeeVpa",  payeeVpa);
        result.put("amount",    "₹" + amountStr);

        // ISO 8583 section
        Map<String, Object> isoSection = new LinkedHashMap<>();
        isoSection.put("mti",          iso.mti);
        isoSection.put("mtiMeaning",   mtiMeaning(iso.mti));
        isoSection.put("bitmap",       iso.bitmap);
        isoSection.put("dataElements", iso.dataElements);
        isoSection.put("rawHex",       iso.rawHex);
        isoSection.put("prettyPrint",  iso.toPrettyString());
        result.put("iso8583", isoSection);

        // NPCI XML section
        Map<String, Object> xmlSection = new LinkedHashMap<>();
        xmlSection.put("messageType", xmlMessageType(operation));
        xmlSection.put("namespace",   "urn:npci:upi:xsd:v2.0");
        xmlSection.put("xml",         xml);
        result.put("npciXml", xmlSection);

        // Protocol note
        result.put("note", Map.of(
            "iso8583",  "Binary protocol used for bank-to-bank debit/credit on NPCI leased line",
            "npciXml",  "ISO 20022 XML used for PSP-to-NPCI and NPCI-to-bank routing messages",
            "nonStop",  "On HPE NonStop: ISO 8583 via KMSF/TANDEM queues, XML via Pathway ServerClass"
        ));

        return ResponseEntity.ok(result);
    }

    /** Explain all ISO 8583 MTI codes used in UPI */
    @GetMapping("/iso8583/mti")
    public ResponseEntity<Object> mtiCodes() {
        return ResponseEntity.ok(Map.of(
            "description", "Message Type Indicator — 4-digit code identifying ISO 8583 message type",
            "format",      "VFCC: Version(1) + Function(1) + Class(2)",
            "codes", Map.of(
                "0200", "Financial Transaction Request — debit/credit initiation",
                "0210", "Financial Transaction Response — debit/credit acknowledgement",
                "0400", "Reversal Request — undo a previous debit",
                "0410", "Reversal Response — reversal acknowledgement",
                "0800", "Network Management Request — heartbeat / status check",
                "0810", "Network Management Response — heartbeat response"
            )
        ));
    }

    /** Explain all ISO 8583 response codes (DE39) */
    @GetMapping("/iso8583/response-codes")
    public ResponseEntity<Object> responseCodes() {
        Map<String, String> codes = new LinkedHashMap<>();
        codes.put("00", "Approved — transaction successful");
        codes.put("01", "Refer to card issuer");
        codes.put("05", "Do not honour — generic decline");
        codes.put("14", "Invalid account number / VPA");
        codes.put("51", "Insufficient funds");
        codes.put("54", "Expired card / account");
        codes.put("61", "Exceeds withdrawal amount limit");
        codes.put("65", "Exceeds withdrawal frequency limit");
        codes.put("91", "Issuer or switch inoperative — timeout");
        codes.put("96", "System malfunction");
        return ResponseEntity.ok(Map.of(
            "description", "ISO 8583 Data Element 39 — Response Code",
            "npciMapping", Map.of(
                "U001", "→ ISO 14 (Invalid account/VPA)",
                "U002", "→ ISO 51 (Insufficient funds)",
                "U003", "→ ISO 61 (Limit exceeded)",
                "U004", "→ ISO 62 (Restricted card/account)",
                "U007", "→ ISO 05 (Do not honour — self transfer)",
                "U008", "→ ISO 14 (Invalid account — dormant)",
                "U009", "→ ISO 14 (Invalid account — bad VPA format)"
            ),
            "codes", codes
        ));
    }

    /** Show a sample NPCI XML message */
    @GetMapping("/xml/sample")
    public ResponseEntity<Object> xmlSample() {
        NpciRequest sample = new NpciRequest();
        sample.setMsgId("MSG4A3B2C1D0E5F6A7B");
        sample.setTxnId("TXN9F8E7D6C5B4A");
        sample.setRrn("RRNABCD1234");
        sample.setPayerVpa("alice@sbi");
        sample.setPayeeVpa("bob@hdfc");
        sample.setPayerAccountNumber("10001234567890");
        sample.setPayeeAccountNumber("20009876543210");
        sample.setAmount(new BigDecimal("500.00"));
        sample.setNpciRoutingCode("SBIN0001");
        sample.setOperation(NpciRequest.OperationType.DEBIT);

        return ResponseEntity.ok(Map.of(
            "description", "Sample NPCI ReqPay XML message (ISO 20022 style)",
            "namespace",   "urn:npci:upi:xsd:v2.0",
            "reqPay",      xmlMapper.toXml(sample),
            "sample_response", xmlMapper.toResponseXml(
                NpciResponse.success("MSG4A3B2C1D0E5F6A7B", "TXN9F8E7D6C5B4A", "RRNABCD1234",
                    NpciRequest.OperationType.DEBIT))
        ));
    }

    private String mtiMeaning(String mti) {
        return switch (mti) {
            case "0200" -> "Financial Transaction Request";
            case "0210" -> "Financial Transaction Response";
            case "0400" -> "Reversal Request";
            case "0410" -> "Reversal Response";
            default     -> "Unknown";
        };
    }

    private String xmlMessageType(NpciRequest.OperationType op) {
        return switch (op) {
            case DEBIT    -> "ReqPay";
            case CREDIT   -> "ReqCredit";
            case REVERSAL -> "ReqRvsl";
        };
    }
}
