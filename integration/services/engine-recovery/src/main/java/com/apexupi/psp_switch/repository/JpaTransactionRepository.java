package com.apexupi.psp_switch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.apexupi.psp_switch.model.TransactionEntity;
import com.apexupi.psp_switch.model.TransactionState;

@Repository
public interface JpaTransactionRepository extends JpaRepository<TransactionEntity, String> {

    Optional<TransactionEntity> findByIdempotencyKey(String idempotencyKey);
    
    Optional<TransactionEntity> findByTxnId(String txnId);
    
    List<TransactionEntity> findByState(TransactionState state);
    
    List<TransactionEntity> findByStateAndFailureReasonIsNotNull(TransactionState state);
}

