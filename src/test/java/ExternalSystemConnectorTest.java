import krematos.connector.ExternalSystemConnector;
import krematos.model.ExternalApiRequest;
import krematos.model.ExternalApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExternalSystemConnectorTest {

    private ExternalSystemConnector connector;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        // Mocking the builder to return our mocked WebClient
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeaders(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        connector = new ExternalSystemConnector(webClientBuilder, "http://test-api.com");
    }

    @Test
    void shouldReturnSuccessOnFirstAttempt() {
        ExternalApiRequest request = createTestRequest();
        ExternalApiResponse successResponse = new ExternalApiResponse(200, "OK", "COMPLETED", 100L);

        // --- Mockování WebClient flow ---
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(), any(Class.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(ExternalApiResponse.class)).thenReturn(Mono.just(successResponse));
        // --- Konec Mockování WebClient flow ---

        StepVerifier.create(connector.sendRequest(request))
                .expectNext(successResponse)
                .verifyComplete();

        // Ověříme, že došlo POUZE k jednomu volání
        verify(webClient, times(1)).post();
    }

    private ExternalApiRequest createTestRequest() {
        // Opraveno: Odstraněna chyba kompilace (double místo BigDecimal) pokud to
        // konstruktor vyžaduje
        // Zde předpokládám, že ExternalApiRequest bere double, dle předchozí analýzy.
        return new ExternalApiRequest("REF-1", 50.00, "001");
    }
}
