package krematos;

import krematos.connector.ExternalApiException;
import krematos.exception.ExternalServiceException;
import krematos.controller.MiddlewareController;
import krematos.model.InternalRequest;
import krematos.model.InternalResponse;
import krematos.service.TransactionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = MiddlewareController.class, excludeAutoConfiguration = {
                Main.class,
                ReactiveSecurityAutoConfiguration.class,
                ReactiveOAuth2ClientAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration.class
})
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
                                .expectStatus().isBadRequest() // Očekává HTTP 400
                                .expectBody()
                                .jsonPath("$.status").isEqualTo(400)
                                .jsonPath("$.error").isEqualTo("Bad Request");
        }

        @Test
        @DisplayName("CHYBA SERVERU: Selhání služby nebo vyčerpání pokusů, vrátí HTTP 503 Service Unavailable")
        void shouldReturn503ServiceUnavailableOnServiceFailure() {

                // Simulace chyby služby (ExternalServiceException s 503)
                when(transactionService.process(any(InternalRequest.class)))
                                .thenReturn(Mono.error(new ExternalServiceException(
                                                "Externí služba je dočasně nedostupná",
                                                "ExternalSystem",
                                                503,
                                                validRequest.getInternalOrderId(),
                                                "Service unavailable",
                                                null,
                                                HttpStatus.SERVICE_UNAVAILABLE)));

                // Provedení volání a ověření
                webTestClient.post().uri(API_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(validRequest)
                                .exchange()
                                .expectStatus().isEqualTo(503) // Očekává HTTP 503
                                .expectBody()
                                .jsonPath("$.status").isEqualTo(503)
                                .jsonPath("$.error").isEqualTo("Service Unavailable")
                                .jsonPath("$.message").value(msg -> assertThat(msg.toString())
                                                .contains("Externí služba je dočasně nedostupná"));

        }
}
