package krematos;

import krematos.connector.ExternalApiException;
import krematos.controller.MiddlewareController;
import krematos.model.InternalRequest;
import krematos.model.InternalResponse;
import krematos.service.TransactionService;
import krematos.security.SecurityConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = MiddlewareController.class, excludeAutoConfiguration = {
                Main.class,
                ReactiveSecurityAutoConfiguration.class // Vypnutí automatické security konfigurace pro testy
}, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class // Ignorování
                                                                                                            // vlastní
                                                                                                            // security
                                                                                                            // konfigurace
))
class MiddlewareControllerTest {

        @Autowired
        private WebTestClient webTestClient;

        @MockBean
        private TransactionService transactionService;

        private InternalRequest validRequest;
        private final String API_URL = "/api/middleware/v1/transaction";

        @BeforeEach
        void setUp() {
                // Příprava validního požadavku před každým testem
                validRequest = new InternalRequest(
                                "INT-ORDER-999",
                                new BigDecimal("50.00"),
                                "EUR",
                                "PAYMENT",
                                Instant.now());
        }

        @Test
        @DisplayName("ÚSPĚCH: Transakce je úspěšně zpracována a vrátí HTTP 200 OK")
        void shouldReturn200OkOnSuccessfulTransaction() {
                // Příprava očekávané odpovědi ze service vrstvy
                InternalResponse successResponse = new InternalResponse(true, "OK", validRequest.getInternalOrderId());

                when(transactionService.process(any(InternalRequest.class)))
                                .thenReturn(Mono.just(successResponse));

                // Provedení volání a ověření
                webTestClient.post().uri(API_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(validRequest)
                                .exchange()
                                .expectStatus().isOk() // Očekává HTTP 200
                                .expectBody(InternalResponse.class)
                                .value(body -> {
                                        assertThat(body.getSuccess()).isTrue();
                                        assertThat(body.getInternalReferenceId()).isEqualTo("INT-ORDER-999");
                                });
        }

        @Test
        @DisplayName("CHYBA KLIENTA: Externí systém odmítl data (4xx), vrátí HTTP 400 Bad Request")
        void shouldReturn400BadRequestOnExternalClientError() {
                String errorMessage = "Neplatný formát dat pro externí systém.";

                // Simulace chyby validace nebo odmítnutí externím systémem
                when(transactionService.process(any(InternalRequest.class)))
                                .thenReturn(Mono.error(new ExternalApiException(errorMessage,
                                                validRequest.getInternalOrderId())));

                // Provedení volání a ověření
                webTestClient.post().uri(API_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(validRequest)
                                .exchange()
                                .expectStatus().isBadRequest() // Očekáváme HTTP 400
                                .expectBody(InternalResponse.class)
                                .value(body -> {
                                        assertThat(body.getSuccess()).isFalse();
                                        assertThat(body.getMessage()).contains(errorMessage);
                                });
        }

        @Test
        @DisplayName("CHYBA SERVERU: Selhání služby nebo vyčerpání pokusů, vrátí HTTP 503 Service Unavailable")
        void shouldReturn503ServiceUnavailableOnServiceFailure() {
                String errorMessage = "API konektor selhal. Externí systém je nedostupný.";

                // Simulace neočekávané chyby nebo vyčerpání retry logiky (RuntimeException)
                when(transactionService.process(any(InternalRequest.class)))
                                .thenReturn(Mono.error(new RuntimeException(errorMessage)));

                // Provedení volání a ověření
                webTestClient.post().uri(API_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(validRequest)
                                .exchange()
                                .expectStatus().isEqualTo(503) // Očekáváme HTTP 503
                                .expectBody(InternalResponse.class)
                                .value(body -> {
                                        // Ověření obsahu
                                        assertThat(body.getSuccess()).isFalse();
                                        assertThat(body.getMessage()).contains("dočasně nedostupná");
                                });
        }
}
