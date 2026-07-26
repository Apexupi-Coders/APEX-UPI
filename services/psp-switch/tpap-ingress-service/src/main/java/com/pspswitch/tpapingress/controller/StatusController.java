package com.pspswitch.tpapingress.controller;

import com.pspswitch.tpapingress.dto.response.ErrorResponse;
import com.pspswitch.tpapingress.dto.response.TransactionStatus;
import com.pspswitch.tpapingress.exception.RequestValidationException;
import com.pspswitch.tpapingress.idempotency.IdempotencyRecord;
import com.pspswitch.tpapingress.idempotency.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.pspswitch.tpapingress.controller.VpaLookupController.validateTxnId;

/**
 * GET /status/{txnId} — poll transaction status.
 * See architecture_spec.md Section 4.5.
 */
@RestController
@RequestMapping("/tpap/api/v1")
@RequiredArgsConstructor
public class StatusController {

    private final IdempotencyRepository idempotencyRepository;

    @GetMapping("/status/{txnId}")
    public ResponseEntity<?> getStatus(@PathVariable String txnId,
                                       @RequestAttribute("tpapId") String tpapId) {
        validateTxnId(txnId, tpapId);

        Optional<IdempotencyRecord> record = idempotencyRepository.findByIdempotencyKey(
                com.pspswitch.tpapingress.service.IdempotencyService.computeKey(tpapId, txnId));

        if (record.isEmpty()) {
            ErrorResponse error = ErrorResponse.builder()
                    .errorCode("TXN_NOT_FOUND")
                    .message("No transaction found for the given txnId")
                    .correlationId(UUID.randomUUID().toString())
                    .timestamp(Instant.now().toString())
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        IdempotencyRecord rec = record.get();
        TransactionStatus status = TransactionStatus.builder()
                .txnId(rec.getTxnId())
                .correlationId(rec.getCorrelationId())
                .eventType(rec.getEventType())
                .currentStatus("ACCEPTED")
                .tpapId(rec.getTpapId())
                .createdAt(rec.getCreatedAt().toString())
                .updatedAt(rec.getCreatedAt().toString())
                .build();

        return ResponseEntity.ok(status);
    }
}
