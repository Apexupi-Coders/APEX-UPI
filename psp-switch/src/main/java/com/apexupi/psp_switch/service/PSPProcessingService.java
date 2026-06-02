package com.apexupi.psp_switch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.apexupi.psp_switch.model.TransactionContext;
import com.apexupi.psp_switch.model.TransactionResult;
import com.apexupi.psp_switch.repository.TransactionStore;
import com.apexupi.psp_switch.service.MonitoringService;
import com.apexupi.psp_switch.service.PSPProcessor;

@Service
@Slf4j
@RequiredArgsConstructor
public class PSPProcessingService implements PSPProcessor {

    private final TransactionStore store;
    private final MonitoringService monitoringService;

    @Override
    public TransactionResult process(TransactionContext ctx) {
        String txnId = ctx.getTxnId();
        
        // 1. CBS DEBIT
        if (!store.debitPayer(txnId, ctx.getPayer(), ctx.getAmount())) {
            monitoringService.addEvent(txnId, "CBS_DEBIT_FAILED");
            return new TransactionResult("FAILED", "CBS_DEBIT_FAILED", "CBS_DEBIT");
        }
        monitoringService.addEvent(txnId, "CBS_DEBIT");
        
        // 2. CBS CREDIT
        if (!store.creditPayee(txnId, ctx.getPayee(), ctx.getAmount())) {
            monitoringService.addEvent(txnId, "CREDIT_FAILED");
            monitoringService.addEvent(txnId, "COMPENSATING");
            
            store.compensatePayerDebit(txnId, ctx.getPayer(), ctx.getAmount());
            monitoringService.addEvent(txnId, "COMPENSATED");
            return new TransactionResult("COMPENSATED", "CREDIT_FAILED_COMPENSATED", "CBS_CREDIT");
        }
        monitoringService.addEvent(txnId, "CBS_CREDIT");
        
        return new TransactionResult("SUCCESS", null, "CBS_CREDIT");
    }

}

