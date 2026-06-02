package com.apexupi.psp_switch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.apexupi.psp_switch.model.LedgerEntity;

@Repository
public interface JpaLedgerRepository extends JpaRepository<LedgerEntity, Long> {
    
    List<LedgerEntity> findByTxnId(String txnId);
    
    List<LedgerEntity> findByType(String type);
    
    List<LedgerEntity> findByTxnIdOrderByTimestampAsc(String txnId);
}

