package com.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditService {

    @Autowired
    private AuditRepository repo;

    public void log(String txnId, String source, String status,
            String payer, String payee, Double amount,
            String stage, String remarks) {

        AuditLog log = new AuditLog();
        log.setTxnId(txnId);
        log.setSource(source);
        log.setStatus(status);
        log.setPayerVpa(payer);
        log.setPayeeVpa(payee);
        log.setAmount(amount);
        log.setStage(stage);
        log.setRemarks(remarks);
        log.setCreatedAt(LocalDateTime.now());

        repo.save(log);
    }

    public List<AuditLog> getAll() {
        return repo.findAll();
    }
}