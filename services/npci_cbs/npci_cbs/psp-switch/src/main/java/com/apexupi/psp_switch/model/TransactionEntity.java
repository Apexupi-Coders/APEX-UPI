

package com.apexupi.psp_switch.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    @Column(name = "txn_id")
    private String txnId;

    @Enumerated(EnumType.STRING)
    private TransactionState state = TransactionState.PENDING;

    private String payer;
    private String payee;
    private double amount;
    private String idempotencyKey;
    private String failureReason;

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(columnDefinition = "TIMESTAMP")
    private LocalDateTime updatedAt;

    @ElementCollection
    @CollectionTable(name = "transaction_timeline", joinColumns = @JoinColumn(name = "txn_id"))
    private List<String> timeline = new ArrayList<>();

    public void updateState(TransactionState newState) {
        this.state = newState;
        this.updatedAt = LocalDateTime.now();
        this.timeline.add(newState.name());
    }
}