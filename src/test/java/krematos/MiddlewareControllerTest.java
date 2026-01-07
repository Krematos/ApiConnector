package krematos;


import krematos.connector.ExternalApiException;
import krematos.controller.MiddlewareController;
import krematos.model.InternalRequest;
import krematos.model.InternalResponse;
import krematos.service.TransactionService;
import krematos.security.SecurityConfig;

import org.junit.jupiter.api.BeforeEach;
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
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;






@WebFluxTest(controllers = MiddlewareController.class,
        excludeAutoConfiguration = {
                Main.class,
                ReactiveSecurityAutoConfiguration.class // 1. Vypne automatickou security
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = SecurityConfig.class // 2. Ignoruje vlastní security konfiguraci
        ))
class  MiddlewareControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TransactionService transactionService;

    private InternalRequest validRequest;
    private final String API_URL = "/api/middleware/v1/transaction";

    @BeforeEach
    void setUp() {
        validRequest = new InternalRequest(
                "INT-ORDER-999",
                new BigDecimal("50.00"),
                "EUR",
                "PAYMENT",
                Instant.now()
        );
    }

    // 1. Test: Úspěšná transakce (HTTP 200 OK)
    @Test
    void shouldReturn200OkOnSuccessfulTransaction() {
        InternalResponse successResponse = new InternalResponse(true, "OK", validRequest.getInternalOrderId());

        // Simulujeme úspěch ze Service vrstvy
        when(transactionService.process(any(InternalRequest.class)))
                .thenReturn(Mono.just(successResponse));

        webTestClient.post().uri(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest)
                .exchange()
                .expectStatus().isOk() // Očekává 200 OK
                .expectBody(InternalResponse.class)
                .consumeWith(response -> {
                    // Ověření obsahu
                    assert response.getResponseBody() != null;
                    assert response.getResponseBody().isSuccess();
                });
    }

    // 2. Test: Chyba klienta (4xx chyba z externího API) -> HTTP 400 Bad Request
    @Test
    void shouldReturn400BadRequestOnExternalClientError() {
        String errorMessage = "Neplatný formát dat pro externí systém.";

        // Simulujeme ExternalApiException (která pochází z 4xx chyby konektoru)
        when(transactionService.process(any(InternalRequest.class)))
                .thenReturn(Mono.error(new ExternalApiException(errorMessage, validRequest.getInternalOrderId())));

        webTestClient.post().uri(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest)
                .exchange()
                .expectStatus().isBadRequest() // Očekává 400 Bad Request
                .expectBody(InternalResponse.class)
                .consumeWith(response -> {
                    // Ověření obsahu
                    assert response.getResponseBody() != null;
                    assert !response.getResponseBody().isSuccess();
                    assert response.getResponseBody().getMessage().contains(errorMessage);
                });
    }

    // 3. Test: Chyba serveru/Nedostupnost (po selhání retry) -> HTTP 503 Service Unavailable
    @Test
    void shouldReturn503ServiceUnavailableOnServiceFailure() {
        String errorMessage = "API konektor selhal. Externí systém je nedostupný.";

        // Simuluje obecnou RuntimeException (která nastane po vyčerpání všech retry pokusů)
        when(transactionService.process(any(InternalRequest.class)))
                .thenReturn(Mono.error(new RuntimeException(errorMessage)));

        webTestClient.post().uri(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest)
                .exchange()
                .expectStatus().isEqualTo(503) // Očekává 503 Service Unavailable
                .expectBody(InternalResponse.class)
                .consumeWith(response -> {
                    // Ověření obsahu
                    assert response.getResponseBody() != null;
                    assert !response.getResponseBody().isSuccess();
                    assert response.getResponseBody().getMessage().contains("dočasně nedostupná");
                });
    }
}
