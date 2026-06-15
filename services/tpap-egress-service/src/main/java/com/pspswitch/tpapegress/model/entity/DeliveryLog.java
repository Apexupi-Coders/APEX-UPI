package com.pspswitch.tpapegress.model.entity;

import com.pspswitch.tpapegress.model.event.EventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for the delivery_logs table.
 * One row per dispatched event capturing the final delivery outcome.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "delivery_logs")
public class DeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "txn_id", nullable = false, length = 100)
    private String txnId;

    @Column(name = "tpap_id", nullable = false, length = 50)
    private String tpapId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(name = "webhook_url", nullable = false, columnDefinition = "TEXT")
    private String webhookUrl;

    /** SUCCESS | FAILED | SKIPPED */
    @Column(nullable = false, length = 20)
    private String status;

    /** Null when SKIPPED or when all attempts threw connection exceptions. */
    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private int attemptNumber = 1;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "delivered_at", nullable = false)
    @Builder.Default
    private Instant deliveredAt = Instant.now();
}
