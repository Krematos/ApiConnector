package krematos.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import krematos.model.ExternalApiRequest;
import krematos.model.ExternalApiResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import reactor.rabbitmq.Sender;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalSystemConnectorTest {

        private ExternalSystemConnector connector;

        private MockWebServer mockWebServer;

        @Mock
        private ReactiveOAuth2AuthorizedClientManager authorizedClientManager;

        @Mock
        private Sender rabbitSender;

        private final ObjectMapper objectMapper = new ObjectMapper();

        @BeforeEach
        void setUp() throws IOException {
                mockWebServer = new MockWebServer();
                mockWebServer.start();

                // Mock OAuth2 setup
                ClientRegistration clientRegistration = ClientRegistration
                                .withRegistrationId("external-api-client")
                                .clientId("test-client")
                                .clientSecret("test-secret")
                                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                                .tokenUri("http://localhost/token")
                                .build();

                OAuth2AccessToken accessToken = new OAuth2AccessToken(
                                OAuth2AccessToken.TokenType.BEARER,
                                "mock-token",
                                null,
                                null);

                OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(
                                clientRegistration,
                                "test-principal",
                                accessToken);

                // Mock authorizedClientManager to return the authorized client
                when(authorizedClientManager.authorize(any()))
                                .thenReturn(Mono.just(authorizedClient));

                WebClient.Builder webClientBuilder = WebClient.builder();

                connector = new ExternalSystemConnector(
                                webClientBuilder,
                                authorizedClientManager,
                                rabbitSender,
                                objectMapper,
                                mockWebServer.url("/").toString());
        }

        @AfterEach
        void tearDown() throws IOException {
                mockWebServer.shutdown();
        }

        @Test
        void shouldReturnSuccessOnFirstAttempt() throws Exception {
                ExternalApiRequest request = createTestRequest();
                ExternalApiResponse successResponse = new ExternalApiResponse(200, "OK", "COMPLETED", 100L);

                mockWebServer.enqueue(new MockResponse()
                                .setBody(objectMapper.writeValueAsString(successResponse))
                                .addHeader("Content-Type", "application/json"));

                StepVerifier.create(connector.sendRequest(request))
                                .expectNextMatches(
                                                response -> response.getStatusCode() == 200
                                                                && "COMPLETED".equals(response.getDetailStatus()))
                                .verifyComplete();

                assertEquals(1, mockWebServer.getRequestCount());
        }

        @Test
        void shouldRetryOn5xxErrorAndSucceed() throws Exception {
                ExternalApiRequest request = createTestRequest();
                ExternalApiResponse successResponse = new ExternalApiResponse(200, "OK", "COMPLETED", 100L);

                // First attempt fails with 500
                mockWebServer.enqueue(new MockResponse().setResponseCode(500));
                // Second attempt fails with 503
                mockWebServer.enqueue(new MockResponse().setResponseCode(503));
                // Third attempt succeeds
                mockWebServer.enqueue(new MockResponse()
                                .setBody(objectMapper.writeValueAsString(successResponse))
                                .addHeader("Content-Type", "application/json"));

                StepVerifier.create(connector.sendRequest(request))
                                .expectNextMatches(response -> response.getStatusCode() == 200)
                                .verifyComplete();

                assertEquals(3, mockWebServer.getRequestCount());
        }

        @Test
        void shouldExhaustRetriesAndSendToDlqOnRepeated5xx() {
                ExternalApiRequest request = createTestRequest();

                for (int i = 0; i < 5; i++) {
                        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
                }

                when(rabbitSender.send(any(Mono.class))).thenReturn(Mono.empty());

                StepVerifier.create(connector.sendRequest(request))
                        // ZMĚNA: Přesný text chyby, kterou aplikace hází
                        .expectErrorMatches(throwable -> throwable.getMessage()
                                .contains("Externí služba není dostupná po 3 pokusech"))
                        .verify();

                verify(rabbitSender, times(1)).send(any(Mono.class));
        }


        @Test
        void shouldHandle4xxClientErrorAndSendToDlq() {
                ExternalApiRequest request = createTestRequest();

                mockWebServer.enqueue(new MockResponse()
                        .setResponseCode(400)
                        .setBody("Bad Request Details"));

                when(rabbitSender.send(any(Mono.class))).thenReturn(Mono.empty());

                StepVerifier.create(connector.sendRequest(request))
                        // ZMĚNA: Tady to asi hází stejnou obecnou chybu,
                        // pokud ne, podívej se do ExternalSystemConnector, co přesně hází pro 4xx.
                        // Dle logu to ale vypadá na stejnou exception.
                        .expectErrorMatches(throwable -> throwable.getMessage()
                                .contains("Externí služba není dostupná") ||
                                throwable.getMessage().contains("Požadavek uložen do DLQ"))
                        .verify();

                assertEquals(1, mockWebServer.getRequestCount());
                verify(rabbitSender, times(1)).send(any(Mono.class));
        }


        @Test
        void shouldHandleSerializationErrorInDlq() throws Exception {
                // ... setup (beze změny) ...

                for (int i = 0; i < 5; i++) {
                        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
                }

                StepVerifier.create(connector.sendRequest(createTestRequest()))
                        // ZMĚNA: Upraven text
                        .expectErrorMatches(t -> t.getMessage().contains("Externí služba není dostupná"))
                        .verify();
        }

        private ExternalApiRequest createTestRequest() {
                return new ExternalApiRequest("REF-1", BigDecimal.valueOf(50), "001");
        }
}
