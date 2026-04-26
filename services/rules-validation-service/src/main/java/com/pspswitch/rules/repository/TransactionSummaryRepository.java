package com.pspswitch.rules.repository;

import com.pspswitch.rules.entity.TransactionSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface TransactionSummaryRepository extends JpaRepository<TransactionSummary, UUID> {

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionSummary t " +
            "WHERE t.payerVpa = :payerVpa AND t.txnDate = :date AND t.status = 'SUCCESS'")
    BigDecimal sumSuccessfulAmountByPayerVpaAndDate(
            @Param("payerVpa") String payerVpa,
            @Param("date") LocalDate date);
}
