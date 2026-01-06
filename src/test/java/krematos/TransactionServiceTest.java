package krematos;

import krematos.connector.ExternalApiException;
import krematos.connector.ExternalSystemConnector;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Použití JUnit 5 s Mockito
class TransactionServiceTest {

        @Mock
        private ExternalSystemConnector externalSystemConnector;

        @Mock
        private TransactionRepository transactionRepository;

        @InjectMocks
        private TransactionService transactionService;

        // Testovací data
        private InternalRequest validRequest;
        private TransactionAudit pendingAudit;

        @BeforeEach
        void setUp() {
                // Příprava dat před každým testem
                validRequest = new InternalRequest(
                        "ORDER-123",
                        BigDecimal.valueOf(1000.0),
                        "CZK",
                        "PAYMENT",
                        null
                );

                pendingAudit = TransactionAudit.builder()
                        .id(1L)
                        .internalOrderId("ORDER-123")
                        .status(AuditStatus.PENDING.name())
                        .build();
        }

        @Test
        @DisplayName("ÚSPĚCH: Transakce projde, uloží se audit a vrátí response")
        void process_Success() {
                // 1. MOCK: Uložení PENDING do DB
                when(transactionRepository.save(any(TransactionAudit.class)))
                        .thenReturn(Mono.just(pendingAudit));

                // 2. MOCK: Úspěšné volání externího API
                ExternalApiResponse apiResponse = new ExternalApiResponse(
                        200,
                        "OK",
                        "COMPLETED",
                        Instant.now().toEpochMilli()
                );
                when(externalSystemConnector.sendRequest(any(ExternalApiRequest.class)))
                        .thenReturn(Mono.just(apiResponse));

                // 3. Spuštění testu pomocí StepVerifier
                StepVerifier.create(transactionService.process(validRequest))
                        .assertNext(response -> {
                                // Ověření návratové hodnoty
                                assertEquals(true, response.isSuccess());
                                assertEquals("ORDER-123", response.getInternalReferenceId());
                        })
                        .verifyComplete();

                // 4. Ověření interakcí (Verify)
                // Repository se musí volat 2x (1x PENDING, 1x SUCCESS)
                verify(transactionRepository, times(2)).save(any(TransactionAudit.class));
                verify(externalSystemConnector, times(1)).sendRequest(any(ExternalApiRequest.class));
        }

        @Test
        @DisplayName("CHYBA API: Externí systém vrátí chybu, audit se uloží jako FAILED")
        void process_ExternalApiFailure() {
                // 1. MOCK: Uložení PENDING
                when(transactionRepository.save(any(TransactionAudit.class)))
                        .thenReturn(Mono.just(pendingAudit));

                // 2. MOCK: Externí API vyhodí chybu
                when(externalSystemConnector.sendRequest(any()))
                        .thenReturn(Mono.error(new RuntimeException("Connection timed out")));

                // 3. Spuštění testu - očekává chybu
                StepVerifier.create(transactionService.process(validRequest))
                        .expectErrorMatches(throwable ->
                                throwable instanceof RuntimeException &&
                                        throwable.getMessage().equals("Connection timed out")
                        )
                        .verify();

                // 4. Ověření, že se uložil stav FAILED
                // Použije ArgumentCaptor, aby zkontroloval, s jakým stavem se repo volalo podruhé
                ArgumentCaptor<TransactionAudit> auditCaptor = ArgumentCaptor.forClass(TransactionAudit.class);
                verify(transactionRepository, times(2)).save(auditCaptor.capture());

                // Poslední uložený stav musí být FAILED
                TransactionAudit failedAudit = auditCaptor.getAllValues().get(1);
                assertEquals(AuditStatus.FAILED.name(), failedAudit.getStatus());
                assertEquals("Connection timed out", failedAudit.getDetails());
        }

        @Test
        @DisplayName("VALIDACE: Neplatná částka (<= 0) vyhodí chybu ihned")
        void process_InvalidAmount() {
                InternalRequest invalidRequest = new InternalRequest(
                        "ORDER-BAD",
                        BigDecimal.ZERO, // Neplatná částka
                        "CZK",
                        "PAYMENT",
                        null
                );

                StepVerifier.create(transactionService.process(invalidRequest))
                        .expectError(ExternalApiException.class) // Očekává vlastní výjimku
                        .verify();

                // Důležité: Repository ani Connector se nesmí volat!
                verifyNoInteractions(transactionRepository);
                verifyNoInteractions(externalSystemConnector);
        }

        @Test
        @DisplayName("EDGE CASE: Externí systém vrátí prázdné Mono (switchIfEmpty)")
        void process_EmptyResponse() {
                // 1. MOCK: Uložení PENDING
                when(transactionRepository.save(any(TransactionAudit.class)))
                        .thenReturn(Mono.just(pendingAudit));

                // 2. MOCK: Prázdná odpověď
                when(externalSystemConnector.sendRequest(any()))
                        .thenReturn(Mono.empty());

                // 3. Spuštění
                StepVerifier.create(transactionService.process(validRequest))
                        .expectErrorMatches(e -> e.getMessage().contains("Prázdná odpověď"))
                        .verify();

                // 4. Ověření, že se to zalogovalo jako FAILED
                verify(transactionRepository, times(2)).save(any());
        }
}
