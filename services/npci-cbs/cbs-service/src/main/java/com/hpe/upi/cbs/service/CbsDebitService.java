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
public class CbsDebitService {

    private final JdbcTemplate debitJdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final CbsGateway cbsGateway;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public CbsDebitService(@Qualifier("debitJdbc") JdbcTemplate debitJdbc,
                           KafkaTemplate<String, String> kafkaTemplate,
                           CbsGateway cbsGateway) {
        this.debitJdbc = debitJdbc;
        this.kafkaTemplate = kafkaTemplate;
        this.cbsGateway = cbsGateway;
    }

    public void processDebit(Map<String, Object> txn) {
        String txnId    = (String) txn.get("txnId");
        String payerVpa = (String) txn.get("payerVpa");
        String payerBank = (String) txn.get("payerBank");
        Object amountObj = txn.get("amount");

        System.out.println("[CBS-DEBIT] Processing debit for txn: " + txnId
            + " | payer: " + payerVpa + " | amount: " + amountObj);

        try {
            BigDecimal amount = new BigDecimal(amountObj.toString());

            // CBS Gateway — balance check before debit
            CbsGateway.AccountRecord payerAcc = cbsGateway.getByVpa(payerVpa);
            if (payerAcc == null) {
                failDebit(txn, txnId, "U001: Payer account not found in CBS: " + payerVpa);
                return;
            }
            if (!cbsGateway.hasSufficientBalance(payerVpa, amount)) {
                failDebit(txn, txnId, "U002: Insufficient balance. Available: ₹"
                    + cbsGateway.getBalance(payerVpa) + ", Required: ₹" + amount);
                return;
            }

            // CBS Gateway — perform actual debit
            boolean debited = cbsGateway.debit(payerVpa, amount, txnId);
            if (!debited) {
                failDebit(txn, txnId, "U002: Debit failed — CBS gateway rejected");
                return;
            }

            // Generate CBS-specific transaction ID
            String cbsTxnId = "CBS-D-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            txn.put("cbsDebitTxnId", cbsTxnId);
            txn.put("payerBalanceAfter", cbsGateway.getBalance(payerVpa).toString());

            // Write to debit ledger DB
            debitJdbc.update(
                "INSERT INTO debit_ledger (txn_id, rrn, payer_vpa, payer_bank, payer_account, amount, balance_after, status, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?::numeric, ?::numeric, 'DEBITED', NOW()) " +
                "ON CONFLICT (txn_id) DO UPDATE SET status='DEBITED', updated_at=NOW()",
                txnId, txn.get("rrn"), payerVpa, payerBank,
                payerAcc.accountNumber, amount.toString(),
                cbsGateway.getBalance(payerVpa).toString()
            );

            System.out.println("[CBS-DEBIT] ✓ Debit recorded in cbs_debit DB for: " + txnId
                + " | cbsTxnId: " + cbsTxnId + " | Balance after: ₹" + cbsGateway.getBalance(payerVpa));

            txn.put("status", "DEBIT_SUCCESS");
            txn.put("debitConfirmedAt", Instant.now().toString());
            kafkaTemplate.send("upi.cbs.debit.confirm", txnId, mapper.writeValueAsString(txn));
            publishDashboardEvent(txnId, txn,
                "CBS DEBIT DB: ₹" + amount + " debited from " + payerVpa
                + " | Balance: ₹" + cbsGateway.getBalance(payerVpa));

        } catch (Exception e) {
            System.err.println("[CBS-DEBIT] Debit failed for " + txnId + ": " + e.getMessage());
            failDebit(txn, txnId, e.getMessage());
        }
    }

    public void processReversal(Map<String, Object> txn) {
        String txnId    = (String) txn.get("txnId");
        String payerVpa = (String) txn.get("payerVpa");
        Object amountObj = txn.get("amount");
        String reversalReason = (String) txn.getOrDefault("reversalReason", "NPCI auto-reversal");

        System.out.println("[CBS-DEBIT] REVERSAL: Crediting payer back for txn: " + txnId);

        try {
            BigDecimal amount = new BigDecimal(amountObj.toString());

            // CBS Gateway — credit back to payer
            cbsGateway.reverseDebit(payerVpa, amount, txnId);
            txn.put("payerBalanceAfterReversal", cbsGateway.getBalance(payerVpa).toString());

            debitJdbc.update(
                "UPDATE debit_ledger SET status='REVERSED', reversal_reason=?, reversed_at=NOW(), updated_at=NOW() WHERE txn_id=?",
                reversalReason, txnId
            );
            debitJdbc.update(
                "INSERT INTO reversal_ledger (txn_id, payer_vpa, amount, reason, reversed_at) " +
                "VALUES (?, ?, ?::numeric, ?, NOW()) ON CONFLICT (txn_id) DO NOTHING",
                txnId, payerVpa, amount.toString(), reversalReason
            );

            System.out.println("[CBS-DEBIT] ✓ Reversal complete for: " + txnId
                + " | ₹" + amount + " returned to " + payerVpa
                + " | Balance: ₹" + cbsGateway.getBalance(payerVpa));

            txn.put("status", "REVERSED");
            txn.put("reversalCompletedAt", Instant.now().toString());
            kafkaTemplate.send("upi.cbs.reversal.confirm", txnId, mapper.writeValueAsString(txn));
            publishDashboardEvent(txnId, txn,
                "CBS DEBIT DB: ₹" + amount + " returned to " + payerVpa
                + " | Balance: ₹" + cbsGateway.getBalance(payerVpa));

        } catch (Exception e) {
            System.err.println("[CBS-DEBIT] Reversal failed for " + txnId + ": " + e.getMessage());
        }
    }

    private void failDebit(Map<String, Object> txn, String txnId, String reason) {
        try {
            txn.put("status", "DEBIT_FAILED");
            txn.put("failureReason", reason);
            publishDashboardEvent(txnId, txn, "CBS DEBIT FAILED: " + reason);
            kafkaTemplate.send("upi.transactions.status", txnId, mapper.writeValueAsString(txn));
        } catch (Exception ex) {
            System.err.println("[CBS-DEBIT] Failed to publish failure status: " + ex.getMessage());
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
            event.put("dbSource", "CBS_DEBIT_DB");
            event.put("timestamp",Instant.now().toString());
            event.put("reversalInitiated", Boolean.TRUE.equals(txn.get("reversalInitiated")));
            if (txn.get("reversalReason") != null) event.put("reversalReason", txn.get("reversalReason").toString());
            if (txn.get("payerBalanceAfter") != null) event.put("payerBalanceAfter", txn.get("payerBalanceAfter").toString());
            if (txn.get("payerBalanceAfterReversal") != null) event.put("payerBalanceAfterReversal", txn.get("payerBalanceAfterReversal").toString());
            kafkaTemplate.send("upi.dashboard.events", txnId, event.toString());
        } catch (Exception e) {
            System.err.println("[CBS-DEBIT] Dashboard event error: " + e.getMessage());
        }
    }
}
