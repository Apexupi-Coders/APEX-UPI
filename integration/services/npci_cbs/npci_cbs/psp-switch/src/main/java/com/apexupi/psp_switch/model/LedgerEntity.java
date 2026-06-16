package com.apexupi.psp_switch.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "ledger")
public class LedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String txnId;

    private String type; // DEBIT, CREDIT, COMPENSATE

    private String fromUpi;

    private String toUpi;

    private double amount;

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime timestamp = LocalDateTime.now();

    private String state; // PENDING, SUCCESS, FAILED
}

