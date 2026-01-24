package krematos;

import krematos.connector.ExternalApiException;
import krematos.connector.ExternalSystemConnector;
import krematos.exception.ValidationException;
import krematos.controller.GlobalExceptionHandler;
import krematos.model.*;
import krematos.repository.TransactionRepository;
import krematos.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Import(GlobalExceptionHandler.class)
class TransactionServiceTest {

        @Mock
        private ExternalSystemConnector externalSystemConnector;

        @Mock
        private TransactionRepository transactionRepository;

        @InjectMocks
        private TransactionService transactionService;

        private InternalRequest validRequest;
        private TransactionAudit pendingAudit;

        @BeforeEach
        void setUp() {
                validRequest = new InternalRequest(
                                "ORDER-123",
                                BigDecimal.valueOf(1000.0),
                                "CZK",
                                "PAYMENT",
                                null);

                pendingAudit = TransactionAudit.builder()
                                .id(1L)
                                .internalOrderId("ORDER-123")
                                .status(AuditStatus.PENDING.name())
                                .build();
        }

        @Test
        @DisplayName("SUCCESS: Transaction is processed, audit is saved and response is returned")
        void process_Success() {
                // Given
                when(transactionRepository.save(any(TransactionAudit.class)))
                                .thenReturn(Mono.just(pendingAudit));

                ExternalApiResponse apiResponse = new ExternalApiResponse(
                                200,
                                "CONFIRM-1",
                                "COMPLETED",
                                Instant.now().toEpochMilli());

                when(externalSystemConnector.sendRequest(any(ExternalApiRequest.class)))
                                .thenReturn(Mono.just(apiResponse));

                // When & Then
                StepVerifier.create(transactionService.process(validRequest))
                                .assertNext(response -> {
                                        assertThat(response.getSuccess()).isTrue();
                                        assertThat(response.getInternalReferenceId()).isEqualTo("ORDER-123");
                                })
                                .verifyComplete();

                // Verify interactions
                verify(transactionRepository, times(2)).save(any(TransactionAudit.class));
                verify(externalSystemConnector, times(1)).sendRequest(any(ExternalApiRequest.class));
        }

        @Test
        @DisplayName("API FAILURE: External system failed, audit is saved as FAILED")
        void process_ExternalApiFailure() {
                // Given
                when(transactionRepository.save(any(TransactionAudit.class)))
                                .thenReturn(Mono.just(pendingAudit));

                when(externalSystemConnector.sendRequest(any()))
                                .thenReturn(Mono.error(new RuntimeException("Connection timed out")));

                // When & Then
                StepVerifier.create(transactionService.process(validRequest))
                                .expectErrorMatches(throwable -> throwable instanceof RuntimeException &&
                                                "Connection timed out".equals(throwable.getMessage()))
                                .verify();

                // Verify status FAILED
                ArgumentCaptor<TransactionAudit> auditCaptor = ArgumentCaptor.forClass(TransactionAudit.class);
                verify(transactionRepository, times(2)).save(auditCaptor.capture());

                TransactionAudit failedAudit = auditCaptor.getAllValues().get(1);
                assertThat(failedAudit.getStatus()).isEqualTo(AuditStatus.FAILED.name());
                assertThat(failedAudit.getDetails()).isEqualTo("Connection timed out");
        }

        @Test
        @DisplayName("VALIDATION: Invalid amount (<= 0) throws exception immediately")
        void doprocess_InvalidAmount() {
                // Given
                InternalRequest invalidRequest = new InternalRequest(
                                "ORDER-BAD",
                                BigDecimal.ZERO,
                                "CZK",
                                "PAYMENT",
                                null);

                // When & Then
                StepVerifier.create(transactionService.process(invalidRequest))
                                .expectError(ValidationException.class)
                                .verify();

                verifyNoInteractions(transactionRepository);
                verifyNoInteractions(externalSystemConnector);
        }

        @Test
        @DisplayName("VALIDATION: Missing currency throws exception immediately")
        void process_MissingCurrency() {
                // Given
                InternalRequest invalidRequest = new InternalRequest(
                                "ORDER-BAD",
                                BigDecimal.TEN,
                                null,
                                "PAYMENT",
                                null);

                // When & Then
                StepVerifier.create(transactionService.process(invalidRequest))
                                .expectError(ValidationException.class)
                                .verify();

                verifyNoInteractions(transactionRepository);
                verifyNoInteractions(externalSystemConnector);
        }

        @Test
        @DisplayName("EDGE CASE: External system returns empty Mono (switchIfEmpty)")
        void process_EmptyResponse() {
                // Given
                when(transactionRepository.save(any(TransactionAudit.class)))
                                .thenReturn(Mono.just(pendingAudit));

                when(externalSystemConnector.sendRequest(any()))
                                .thenReturn(Mono.empty());

                // When & Then
                StepVerifier.create(transactionService.process(validRequest))
                                .expectErrorMatches(e -> e.getMessage().contains("Prázdná odpověď"))
                                .verify();

                verify(transactionRepository, times(2)).save(any());

                // Verify FAILED status
                ArgumentCaptor<TransactionAudit> auditCaptor = ArgumentCaptor.forClass(TransactionAudit.class);
                verify(transactionRepository, times(2)).save(auditCaptor.capture());

                TransactionAudit failedAudit = auditCaptor.getAllValues().get(1);
                assertThat(failedAudit.getStatus()).isEqualTo(AuditStatus.FAILED.name());
        }
}
