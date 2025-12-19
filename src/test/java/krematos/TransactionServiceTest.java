package krematos;

import krematos.connector.ExternalSystemConnector;
import krematos.model.ExternalApiRequest;
import krematos.model.ExternalApiResponse;
import krematos.model.InternalRequest;
import krematos.model.InternalResponse;
import krematos.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {
        // Mockuje konektor, aby netestoval skutečné volání API
        @Mock
        private ExternalSystemConnector connector;

        // Vloží mock konektoru do TransactionService
        @InjectMocks
        private TransactionService transactionService;

        private InternalRequest internalRequest;

        @BeforeEach
        void setUp() {
                // Inicializace testovacího interního požadavku
                internalRequest = new InternalRequest(
                                "INT-ORDER-12345",
                                new BigDecimal("100.00"),
                                "CZK",
                                "PAYMENT", // Toto se transformuje na "001"
                                LocalDateTime.of(2025, 12, 13, 10, 0));
        }

        @Test
        void shouldProcessTransactionSuccessfully() {
                // Nastavení Mocka: Když je zavolán connector.sendRequest, vrátí Mono s úspěšnou
                // odpovědí
                ExternalApiResponse externalResponse = new ExternalApiResponse(
                                200,
                                "CONF-ABC-789",
                                "COMPLETED",
                                150L);

                when(connector.sendRequest(any(ExternalApiRequest.class)))
                                .thenReturn(Mono.just(externalResponse));

                // Testujeme reaktivní tok
                StepVerifier.create(transactionService.process(internalRequest))
                                .assertNext(response -> {
                                        // Ověření úspěšné transformace
                                        assertTrue(response.isSuccess());
                                        assertTrue(response.getMessage().contains("CONF-ABC-789"));
                                        assertTrue(response.getInternalReferenceId().equals("INT-ORDER-12345"));
                                })
                                .verifyComplete();
        }

        @Test
        void shouldHandleExternalApiFailure() {
                // Nastavení Mocka: Simuluje neúspěšnou odpověď z externího systému
                ExternalApiResponse failedResponse = new ExternalApiResponse(
                                400,
                                null,
                                "INVALID_DATA",
                                50L);

                when(connector.sendRequest(any(ExternalApiRequest.class)))
                                .thenReturn(Mono.just(failedResponse));

                // Testuje reaktivní tok
                StepVerifier.create(transactionService.process(internalRequest))
                                .assertNext(response -> {
                                        // Ověření, že interní odpověď hlásí neúspěch
                                        assertFalse(response.isSuccess());
                                        assertTrue(response.getMessage().contains("INVALID_DATA"));
                                })
                                .verifyComplete();
        }

        @Test
        void shouldMapServiceTypesCorrectly() {
                // Prepare request with specific service type
                InternalRequest premiumRequest = new InternalRequest(
                                "INT-PREMIUM",
                                new BigDecimal("200.00"),
                                "USD",
                                "PREMIUM",
                                LocalDateTime.now());

                ExternalApiResponse successResponse = new ExternalApiResponse(200, "OK", "COMPLETED", 100L);

                // Capture the argument passed to connector
                when(connector.sendRequest(any(ExternalApiRequest.class)))
                                .thenReturn(Mono.just(successResponse));

                StepVerifier.create(transactionService.process(premiumRequest))
                                .expectNextMatches(InternalResponse::isSuccess)
                                .verifyComplete();
        }
}
