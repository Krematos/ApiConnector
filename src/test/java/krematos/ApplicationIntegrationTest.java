package krematos;

import krematos.model.InternalRequest;
import krematos.model.InternalResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "security.api-key=test-api-key"
})
@Testcontainers
class ApplicationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    static MockWebServer mockWebServer;

    @BeforeAll
    static void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("external.api.base-url", () -> mockWebServer.url("/").toString());
    }

    @Test
    void shouldProcessTransactionSuccessfully() {
        // 1. Prepare Request
        InternalRequest request = new InternalRequest(
                "INT-TEST-001",
                new BigDecimal("100.50"),
                "USD",
                "PAYMENT",
                LocalDateTime.now());

        // 2. Mock External API Response
        mockWebServer.enqueue(new MockResponse()
                .setBody(
                        "{\"code\": 200, \"externalReferenceId\": \"EXT-123\", \"status\": \"PROCESSED\", \"timestamp\": 123456789}")
                .addHeader("Content-Type", "application/json"));

        // 3. Send Request to Middleware
        webTestClient.post()
                .uri("/api/middleware/v1/transaction")
                .header("X-API-KEY", "test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(InternalResponse.class)
                .consumeWith(response -> {
                    InternalResponse responseBody = response.getResponseBody();
                    assertThat(responseBody).isNotNull();
                    assertThat(responseBody.isSuccess()).isTrue();
                    assertThat(responseBody.getInternalReferenceId()).isEqualTo("INT-TEST-001");
                });
    }
}
