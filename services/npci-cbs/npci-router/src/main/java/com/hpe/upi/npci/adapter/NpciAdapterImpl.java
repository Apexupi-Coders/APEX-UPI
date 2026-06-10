package com.hpe.upi.npci.adapter;

import com.hpe.upi.npci.registry.VpaRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NpciAdapterImpl — full protocol adapter for the NPCI UPI network.
 *
 * This implementation translates between the internal UPI message format
 * and two real NPCI protocol standards:
 *
 *   1. ISO 8583 — binary financial messaging protocol
 *      Used for bank-to-bank debit/credit/reversal operations.
 *      The Iso8583Codec encodes NpciRequest → bitmap message and
 *      decodes response codes back into NpciResponse.
 *
 *   2. NPCI XML — ISO 20022-based XML messages
 *      Used for PSP-to-NPCI and NPCI-to-bank routing messages.
 *      The NpciXmlMapper generates ReqPay/ReqCredit/ReqRvsl XML
 *      and parses RespPay/RespRvsl XML back into NpciResponse.
 *
 * In a real HPE NonStop deployment:
 *   - ISO 8583 messages go via TANDEM message queues to bank switches
 *   - XML messages go via Pathway ServerClass to NPCI leased line
 *   - HSM (Hardware Security Module) handles PIN block encryption
 *   - This adapter is the exact boundary where NonStop integration happens
 *
 * Validation (U001-U009 NPCI error codes):
 *   - VPA format, registry lookup, account status
 *   - Duplicate detection, daily limit, amount range
 */
@Component
public class NpciAdapterImpl implements NpciProtocolAdapter {

    // RBI mandated UPI limits
    private static final BigDecimal MAX_TXN_AMOUNT = new BigDecimal("100000");  // ₹1,00,000
    private static final BigDecimal MIN_TXN_AMOUNT = new BigDecimal("1");        // ₹1
    private static final BigDecimal DAILY_LIMIT    = new BigDecimal("1000000"); // ₹10,00,000

    // NPCI Standard Error Codes
    public static final String U001_VPA_NOT_FOUND      = "U001";
    public static final String U002_INSUFFICIENT_FUNDS = "U002";
    public static final String U003_LIMIT_EXCEEDED     = "U003";
    public static final String U004_ACCOUNT_FROZEN     = "U004";
    public static final String U005_BANK_UNAVAILABLE   = "U005";
    public static final String U006_DUPLICATE_TXN      = "U006";
    public static final String U007_SELF_TRANSFER      = "U007";
    public static final String U008_ACCOUNT_DORMANT    = "U008";
    public static final String U009_INVALID_VPA_FORMAT = "U009";

    // Bank routing codes — VPA handle → IFSC prefix
    private static final Map<String, String> BANK_ROUTING_CODES = new HashMap<>();
    static {
        BANK_ROUTING_CODES.put("sbi",    "SBIN0001");
        BANK_ROUTING_CODES.put("hdfc",   "HDFC0001");
        BANK_ROUTING_CODES.put("icici",  "ICIC0001");
        BANK_ROUTING_CODES.put("axis",   "UTIB0001");
        BANK_ROUTING_CODES.put("kotak",  "KKBK0001");
        BANK_ROUTING_CODES.put("pnb",    "PUNB0001");
        BANK_ROUTING_CODES.put("bob",    "BARB0001");
        BANK_ROUTING_CODES.put("canara", "CNRB0001");
        BANK_ROUTING_CODES.put("ybl",    "YESB0001");
        BANK_ROUTING_CODES.put("oksbi",  "SBIN0002");
        BANK_ROUTING_CODES.put("okaxis", "UTIB0002");
        BANK_ROUTING_CODES.put("paytm",  "PYTM0001");
    }

    // Duplicate detection — last 10,000 txnIds
    private final java.util.Set<String> processedTxnIds =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    // Daily spend tracker: vpa → total spent today
    private final Map<String, BigDecimal> dailySpend =
        new java.util.concurrent.ConcurrentHashMap<>();

    private final VpaRegistry     vpaRegistry;
    private final NpciXmlMapper   xmlMapper;
    private final Iso8583Codec    iso8583Codec;

    public NpciAdapterImpl(VpaRegistry vpaRegistry,
                            NpciXmlMapper xmlMapper,
                            Iso8583Codec iso8583Codec) {
        this.vpaRegistry  = vpaRegistry;
        this.xmlMapper    = xmlMapper;
        this.iso8583Codec = iso8583Codec;
    }

    // ── Protocol Translation: Internal Map → NpciRequest ─────────────────────

    @Override
    public NpciRequest toNpciRequest(Map<String, Object> txn,
                                      NpciRequest.OperationType operation) {
        NpciRequest req = new NpciRequest();
        req.setMsgId("MSG" + UUID.randomUUID().toString()
            .replace("-", "").substring(0, 16).toUpperCase());
        req.setTxnId((String) txn.get("txnId"));
        req.setRrn((String) txn.get("rrn"));
        req.setPayerVpa((String) txn.get("payerVpa"));
        req.setPayeeVpa((String) txn.get("payeeVpa"));
        req.setPayerBank((String) txn.get("payerBank"));
        req.setPayeeBank((String) txn.get("payeeBank"));
        req.setOperation(operation);
        req.setSimulateFailure(Boolean.TRUE.equals(txn.get("simulateFailure")));

        Object amountObj = txn.get("amount");
        if (amountObj != null) req.setAmount(new BigDecimal(amountObj.toString()));

        if (operation == NpciRequest.OperationType.REVERSAL) {
            req.setReversalReason((String) txn.getOrDefault(
                "reversalReason", "NPCI auto-reversal — credit bank timeout"));
        }

        // Routing code — prefer IFSC from registry, fallback to handle mapping
        String vpaForRouting = (operation == NpciRequest.OperationType.CREDIT)
            ? (String) txn.get("payeeVpa")
            : (String) txn.get("payerVpa");
        req.setNpciRoutingCode(resolveRoutingCode(vpaForRouting));
        req.setRequestedAt(Instant.now());

        // Enrich with resolved account numbers from VPA registry
        VpaRegistry.VpaRecord payerRec = vpaRegistry.lookup((String) txn.get("payerVpa"));
        VpaRegistry.VpaRecord payeeRec = vpaRegistry.lookup((String) txn.get("payeeVpa"));
        if (payerRec != null) req.setPayerAccountNumber(payerRec.accountNumber);
        if (payeeRec != null) req.setPayeeAccountNumber(payeeRec.accountNumber);

        // ── ISO 8583 encoding ─────────────────────────────────────────────────
        Iso8583Codec.Iso8583Message iso = iso8583Codec.encode(req);
        req.setIso8583Bitmap(iso.bitmap);
        req.setIso8583RawHex(iso.rawHex);

        System.out.println("[NPCI-ADAPTER] ISO 8583 encoded:");
        System.out.println(iso.toPrettyString());

        // ── NPCI XML generation ───────────────────────────────────────────────
        String xml = xmlMapper.toXml(req);
        req.setXmlPayload(xml);

        System.out.println("[NPCI-ADAPTER] NPCI XML generated:");
        System.out.println(xml);

        System.out.println("[NPCI-ADAPTER] Request summary: " + req);
        return req;
    }

    // ── Protocol Translation: NpciResponse → Internal Map ────────────────────

    @Override
    public Map<String, Object> fromNpciResponse(NpciResponse response) {
        Map<String, Object> txn = new HashMap<>();
        txn.put("txnId",           response.getTxnId());
        txn.put("rrn",             response.getRrn());
        txn.put("npciMsgId",       response.getMsgId());
        txn.put("npciTxnId",       response.getNpciTransactionId());
        txn.put("responseCode",    response.getResponseCode());
        txn.put("responseMessage", response.getResponseMessage());
        txn.put("iso8583Code",     response.getResponseCode()); // same code, ISO 8583 DE39
        txn.put("npciRespondedAt", response.getRespondedAt() != null
            ? response.getRespondedAt().toString() : Instant.now().toString());

        // Generate XML response representation
        String responseXml = xmlMapper.toResponseXml(response);
        txn.put("npciResponseXml", responseXml);

        System.out.println("[NPCI-ADAPTER] NPCI XML response:");
        System.out.println(responseXml);

        // Map to internal status
        switch (response.getOperation()) {
            case DEBIT    -> txn.put("status", response.isSuccess() ? "DEBIT_SUCCESS"  : "DEBIT_FAILED");
            case CREDIT   -> txn.put("status", response.isSuccess() ? "CREDIT_SUCCESS" : "CREDIT_FAILED");
            case REVERSAL -> txn.put("status", response.isSuccess() ? "REVERSED"       : "REVERSAL_FAILED");
        }

        System.out.println("[NPCI-ADAPTER] fromNpciResponse: txnId=" + response.getTxnId()
            + " status=" + txn.get("status")
            + " ISO8583-code=" + response.getResponseCode());
        return txn;
    }

    // ── Validation (U001–U009) ────────────────────────────────────────────────

    @Override
    public ValidationResult validate(Map<String, Object> txn) {
        String txnId    = (String) txn.get("txnId");
        String payerVpa = (String) txn.get("payerVpa");
        String payeeVpa = (String) txn.get("payeeVpa");
        Object amountObj = txn.get("amount");

        // 1. Mandatory fields
        if (txnId == null || txnId.isBlank())
            return ValidationResult.fail("MISSING_TXN_ID", "Transaction ID is required");

        // 2. Duplicate detection
        if (processedTxnIds.contains(txnId))
            return ValidationResult.fail(U006_DUPLICATE_TXN,
                "Duplicate transaction detected: " + txnId);

        // 3. VPA format
        if (payerVpa == null || !payerVpa.contains("@"))
            return ValidationResult.fail(U009_INVALID_VPA_FORMAT,
                "Payer VPA must contain @: " + payerVpa);
        if (payeeVpa == null || !payeeVpa.contains("@"))
            return ValidationResult.fail(U009_INVALID_VPA_FORMAT,
                "Payee VPA must contain @: " + payeeVpa);

        // 4. Self transfer
        if (payerVpa.equalsIgnoreCase(payeeVpa))
            return ValidationResult.fail(U007_SELF_TRANSFER,
                "Payer and payee VPA cannot be the same");

        // 5. VPA registry lookup
        VpaRegistry.VpaRecord payerRecord = vpaRegistry.lookup(payerVpa);
        VpaRegistry.VpaRecord payeeRecord = vpaRegistry.lookup(payeeVpa);
        if (payerRecord == null)
            return ValidationResult.fail(U001_VPA_NOT_FOUND,
                "Payer VPA not registered: " + payerVpa);
        if (payeeRecord == null)
            return ValidationResult.fail(U001_VPA_NOT_FOUND,
                "Payee VPA not registered: " + payeeVpa);

        // 6. Account status
        if (payerRecord.isFrozen())
            return ValidationResult.fail(U004_ACCOUNT_FROZEN,
                "Payer account is frozen: " + payerVpa);
        if (payerRecord.isDormant())
            return ValidationResult.fail(U008_ACCOUNT_DORMANT,
                "Payer account is dormant: " + payerVpa);
        if (payeeRecord.isFrozen())
            return ValidationResult.fail(U004_ACCOUNT_FROZEN,
                "Payee account is frozen: " + payeeVpa);
        if (payeeRecord.isDormant())
            return ValidationResult.fail(U008_ACCOUNT_DORMANT,
                "Payee account is dormant: " + payeeVpa);

        // 7. Amount validation
        if (amountObj == null)
            return ValidationResult.fail("MISSING_AMOUNT", "Amount is required");
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountObj.toString());
        } catch (NumberFormatException e) {
            return ValidationResult.fail("INVALID_AMOUNT",
                "Amount is not a valid number: " + amountObj);
        }
        if (amount.compareTo(MIN_TXN_AMOUNT) < 0)
            return ValidationResult.fail(U003_LIMIT_EXCEEDED,
                "Amount must be at least ₹" + MIN_TXN_AMOUNT);
        if (amount.compareTo(MAX_TXN_AMOUNT) > 0)
            return ValidationResult.fail(U003_LIMIT_EXCEEDED,
                "Amount ₹" + amount + " exceeds per-transaction limit of ₹" + MAX_TXN_AMOUNT);

        // 8. Daily limit
        BigDecimal todaySpend = dailySpend.getOrDefault(payerVpa, BigDecimal.ZERO);
        if (todaySpend.add(amount).compareTo(DAILY_LIMIT) > 0)
            return ValidationResult.fail(U003_LIMIT_EXCEEDED,
                "Daily UPI limit of ₹" + DAILY_LIMIT + " exceeded for: " + payerVpa);

        // Mark processed + track spend
        processedTxnIds.add(txnId);
        if (processedTxnIds.size() > 10000) processedTxnIds.iterator().remove();
        dailySpend.merge(payerVpa, amount, BigDecimal::add);

        System.out.println("[NPCI-ADAPTER] Validation OK: txnId=" + txnId
            + " payer=" + payerVpa + " payee=" + payeeVpa + " amount=₹" + amount);
        return ValidationResult.ok();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String resolveRoutingCode(String vpa) {
        if (vpa == null || !vpa.contains("@")) return "UNKN0001";
        VpaRegistry.VpaRecord record = vpaRegistry.lookup(vpa);
        if (record != null) return record.ifsc;
        String handle = vpa.split("@")[1].toLowerCase();
        return BANK_ROUTING_CODES.getOrDefault(handle, "UNKN0001");
    }
}
