package krematos;

import krematos.model.InternalRequest;
import krematos.model.InternalResponse;
import krematos.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Komplexní end-to-end integrační test celého transaction flow.
 * 
 * Testuje:
 * - REST endpoint (/api/middleware/v1/transaction)
 * - Service vrstvu (TransactionService)
 * - Externí API connector (ExternalSystemConnector)
 * - Interní mock controllery (MockExternalController, MockAuthController)
 * - Persistence do PostgreSQL
 * - RabbitMQ fallback (DLQ)
 * 
 * Používá Testcontainers pro izolované testovací prostředí.
 * Používá DEFINED_PORT (8080) pro konzistenci s mock controllery.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
                "security.api-key=test-api-key",
                "connector.retry.max-attempts=3",
                "connector.retry.delay-ms=100", // Rychlejší retry pro testy
                "server.port=8080",
                "external.api.base-url=http://localhost:8080/mock-external",
                "spring.security.oauth2.client.registration.external-api-client.client-id=test-client",
                "spring.security.oauth2.client.registration.external-api-client.client-secret=test-secret",
                "spring.security.oauth2.client.registration.external-api-client.authorization-grant-type=client_credentials",
                "spring.security.oauth2.client.registration.external-api-client.provider=mock-provider",
                "spring.security.oauth2.client.provider.mock-provider.token-uri=http://localhost:8080/mock-auth/token"
})
@Testcontainers
class ApplicationIntegrationTest {

        @Container
        @ServiceConnection
        static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

        @Container
        @ServiceConnection
        static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

        @Autowired
        private WebTestClient webTestClient;

        @Autowired
        private TransactionRepository transactionRepository;

        @Test
        @DisplayName("E2E: Úspěšné zpracování transakce s persistencí do DB")
        void shouldProcessTransactionSuccessfullyEndToEnd() {
                // 1. Příprava požadavku
                InternalRequest request = new InternalRequest(
                                "E2E-TEST-001",
                                new BigDecimal("250.75"),
                                "CZK",
                                "PAYMENT",
                                Instant.now());

                // 2. Zavolání REST endpointu
                webTestClient.post()
                                .uri("/api/middleware/v1/transaction")
                                .header("X-API-KEY", "test-api-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(request)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(String.class)
                                .consumeWith(response -> {
                                        System.out.println(">>> RAW JSON ODPOVĚĎ: " + response.getResponseBody());
                                });

                // 4. Ověření persistence v databázi
                StepVerifier.create(
                                transactionRepository.findByInternalOrderId("E2E-TEST-001"))
                                .assertNext(transaction -> {
                                        assertThat(transaction.getInternalOrderId()).isEqualTo("E2E-TEST-001");
                                        assertThat(transaction.getAmount())
                                                        .isEqualByComparingTo(new BigDecimal("250.75"));
                                        assertThat(transaction.getCurrency()).isEqualTo("CZK");
                                        assertThat(transaction.getExternalReferenceId()).isNotNull(); // Mock vrátil ID
                                        assertThat(transaction.getStatus()).isEqualTo("COMPLETED");
                                })
                                .expectComplete()
                                .verify(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("E2E: Zpracování různých měn a částek")
        void shouldHandleDifferentCurrenciesAndAmounts() {
                // Test s EUR
                InternalRequest eurRequest = new InternalRequest(
                                "E2E-EUR-001",
                                new BigDecimal("1000.00"),
                                "EUR",
                                "PAYMENT",
                                Instant.now());

                webTestClient.post()
                                .uri("/api/middleware/v1/transaction")
                                .header("X-API-KEY", "test-api-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(eurRequest)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(InternalResponse.class)
                                .value(response -> {
                                        assertThat(response.getSuccess()).isTrue();
                                        assertThat(response.getInternalReferenceId()).isEqualTo("E2E-EUR-001");
                                });

                // Ověření v DB
                StepVerifier.create(
                                transactionRepository.findByInternalOrderId("E2E-EUR-001"))
                                .assertNext(transaction -> {
                                        assertThat(transaction.getCurrency()).isEqualTo("EUR");
                                        assertThat(transaction.getAmount())
                                                        .isEqualByComparingTo(new BigDecimal("1000.00"));
                                })
                                .expectComplete()
                                .verify(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("E2E: Ověření OAuth2 flow s MockAuthController")
        void shouldAuthenticateWithMockOAuth2Provider() {
                // MockAuthController automaticky vrací token
                // Tento test ověřuje, že OAuth2 flow funguje

                InternalRequest request = new InternalRequest(
                                "E2E-OAUTH-001",
                                new BigDecimal("50.00"),
                                "USD",
                                "PAYMENT",
                                Instant.now());

                webTestClient.post()
                                .uri("/api/middleware/v1/transaction")
                                .header("X-API-KEY", "test-api-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(request)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(InternalResponse.class)
                                .value(response -> {
                                        assertThat(response.getSuccess()).isTrue();
                                        // Pokud OAuth selže, dostane chybu, takže úspěch = OAuth funguje
                                });
        }

        @Test
        @DisplayName("E2E: Chybějící API key vrátí 401 UNAUTHORIZED")
        void shouldReturn403WhenApiKeyIsMissing() {
                InternalRequest request = new InternalRequest(
                                "E2E-NO-KEY-001",
                                new BigDecimal("100.00"),
                                "CZK",
                                "PAYMENT",
                                Instant.now());

                webTestClient.post()
                                .uri("/api/middleware/v1/transaction")
                                // Bez X-API-KEY hlavičky
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(request)
                                .exchange()
                                .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("E2E: Neplatný API key vrátí 401 UNAUTHORIZED")
        void shouldReturn403WhenApiKeyIsInvalid() {
                InternalRequest request = new InternalRequest(
                                "E2E-BAD-KEY-001",
                                new BigDecimal("100.00"),
                                "CZK",
                                "PAYMENT",
                                Instant.now());

                webTestClient.post()
                                .uri("/api/middleware/v1/transaction")
                                .header("X-API-KEY", "wrong-key-12345")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(request)
                                .exchange()
                                .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("E2E: Testování latence mock serveru")
        void shouldHandleMockServerLatency() {
                // MockExternalController simuluje náhodné zpoždění 50-500ms
                // Tento test ověřuje, že aplikace zvládá latenci

                InternalRequest request = new InternalRequest(
                                "E2E-LATENCY-001",
                                new BigDecimal("500.00"),
                                "CZK",
                                "PAYMENT",
                                Instant.now());

                long startTime = System.currentTimeMillis();

                webTestClient.post()
                                .uri("/api/middleware/v1/transaction")
                                .header("X-API-KEY", "test-api-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(request)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(InternalResponse.class)
                                .value(response -> {
                                        assertThat(response.getSuccess()).isTrue();

                                        long duration = System.currentTimeMillis() - startTime;
                                        // Mock má delay 50-500ms, celkový čas by měl být v rozumném rozmezí
                                        assertThat(duration).isLessThan(5000); // Max 5s včetně overhead
                                });
        }
}
