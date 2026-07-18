package com.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditConsumerTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuditConsumer auditConsumer;

    @Test
    void testConsumeValidMessage() {
        // Arrange
        String validJson = "{" +
                "\"txnId\":\"TXN-12345\"," +
                "\"source\":\"tpap-ingress\"," +
                "\"status\":\"SUBMITTED\"," +
                "\"payer\":\"user@bank\"," +
                "\"payee\":\"merchant@bank\"," +
                "\"amount\":500.0," +
                "\"stage\":\"INITIATION\"," +
                "\"remarks\":\"Payment for order\"" +
                "}";

        // Act
        auditConsumer.consume(validJson);

        // Assert
        verify(auditService, times(1)).log(
                "TXN-12345",
                "tpap-ingress",
                "SUBMITTED",
                "user@bank",
                "merchant@bank",
                500.0,
                "INITIATION",
                "Payment for order"
        );
    }

    @Test
    void testConsumeInvalidMessage() {
        // Arrange
        String invalidJson = "{ invalid_json }";

        // Act
        auditConsumer.consume(invalidJson);

        // Assert
        // The consumer catches the exception and logs it, so the service should never be called
        verify(auditService, never()).log(anyString(), anyString(), anyString(), anyString(), anyString(), anyDouble(), anyString(), anyString());
    }
}
