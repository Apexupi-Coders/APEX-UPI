package com.hpe.upi.cbs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hpe.upi.cbs.gateway.CbsGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class CbsCreditService {

    private final JdbcTemplate creditJdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final CbsGateway cbsGateway;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public CbsCreditService(@Qualifier("creditJdbc") JdbcTemplate creditJdbc,
                            KafkaTemplate<String, String> kafkaTemplate,
                            CbsGateway cbsGateway) {
        this.creditJdbc = creditJdbc;
        this.kafkaTemplate = kafkaTemplate;
        this.cbsGateway = cbsGateway;
    }

    public void processCredit(Map<String, Object> txn) {
        String txnId    = (String) txn.get("txnId");
        String payeeVpa = (String) txn.get("payeeVpa");
        String payeeBank = (String) txn.get("payeeBank");
        Object amountObj = txn.get("amount");

        System.out.println("[CBS-CREDIT] Processing credit for txn: " + txnId
            + " | payee: " + payeeVpa + " | amount: " + amountObj);

        try {
            BigDecimal amount = new BigDecimal(amountObj.toString());

            // CBS Gateway — account existence check
            CbsGateway.AccountRecord payeeAcc = cbsGateway.getByVpa(payeeVpa);
            if (payeeAcc == null) {
                System.err.println("[CBS-CREDIT] Payee account not found: " + payeeVpa + " — NPCI will initiate reversal");
                return; // NPCI will timeout and reverse
            }

            // CBS Gateway — perform credit
            cbsGateway.credit(payeeVpa, amount, txnId);
            txn.put("payeeBalanceAfter", cbsGateway.getBalance(payeeVpa).toString());

            // Generate CBS credit transaction ID
            String cbsTxnId = "CBS-C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            txn.put("cbsCreditTxnId", cbsTxnId);

            // Write to credit ledger DB
            creditJdbc.update(
                "INSERT INTO credit_ledger (txn_id, rrn, payee_vpa, payee_bank, payee_account, amount, balance_after, status, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?::numeric, ?::numeric, 'CREDITED', NOW()) " +
                "ON CONFLICT (txn_id) DO UPDATE SET status='CREDITED', updated_at=NOW()",
                txnId, txn.get("rrn"), payeeVpa, payeeBank,
                payeeAcc.accountNumber, amount.toString(),
                cbsGateway.getBalance(payeeVpa).toString()
            );

            System.out.println("[CBS-CREDIT] ✓ Credit recorded in cbs_credit DB for: " + txnId
                + " | cbsTxnId: " + cbsTxnId + " | Balance after: ₹" + cbsGateway.getBalance(payeeVpa));

            txn.put("status", "CREDIT_SUCCESS");
            txn.put("creditConfirmedAt", Instant.now().toString());
            kafkaTemplate.send("upi.cbs.credit.confirm", txnId, mapper.writeValueAsString(txn));
            publishDashboardEvent(txnId, txn,
                "CBS CREDIT DB: ₹" + amount + " credited to " + payeeVpa
                + " | Balance: ₹" + cbsGateway.getBalance(payeeVpa));

        } catch (Exception e) {
            System.err.println("[CBS-CREDIT] Credit failed for " + txnId + ": " + e.getMessage());
        }
    }

    private void publishDashboardEvent(String txnId, Map<String, Object> txn, String message) {
        try {
            ObjectNode event = mapper.createObjectNode();
            event.put("txnId",    txnId);
            event.put("status",   txn.getOrDefault("status", "UNKNOWN").toString());
            event.put("payerVpa", txn.getOrDefault("payerVpa", "").toString());
            event.put("payeeVpa", txn.getOrDefault("payeeVpa", "").toString());
            event.put("payerName",txn.getOrDefault("payerName", "").toString());
            event.put("payeeName",txn.getOrDefault("payeeName", "").toString());
            event.put("payerBank",txn.getOrDefault("payerBank", "").toString());
            event.put("payeeBank",txn.getOrDefault("payeeBank", "").toString());
            event.put("amount",   txn.getOrDefault("amount", "0").toString());
            event.put("rrn",      txn.getOrDefault("rrn", "").toString());
            event.put("message",  message);
            event.put("dbSource", "CBS_CREDIT_DB");
            event.put("timestamp",Instant.now().toString());
            if (txn.get("payeeBalanceAfter") != null) event.put("payeeBalanceAfter", txn.get("payeeBalanceAfter").toString());
            kafkaTemplate.send("upi.dashboard.events", txnId, event.toString());
        } catch (Exception e) {
            System.err.println("[CBS-CREDIT] Dashboard event error: " + e.getMessage());
        }
    }
}
