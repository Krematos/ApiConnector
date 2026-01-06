package krematos;

import krematos.model.InternalRequest;
import krematos.model.InternalResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChaosIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

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
    @Order(1)
    void shouldHandleDatabaseDowntimeGracefully() {
        // Stop Database
        postgres.stop();

        try {
            InternalRequest request = new InternalRequest(
                    "CHAOS-DB-001",
                    new BigDecimal("100.50"),
                    "USD",
                    "PAYMENT",
                    LocalDateTime.now());

            webTestClient.post()
                    .uri("/api/middleware/v1/transaction")
                    .header("X-API-KEY", "test-api-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().is5xxServerError();

        } finally {
            // Restart Database for other tests
            postgres.start();
        }
    }

    @Test
    @Order(2)
    void shouldHandleRabbitMQDowntimeDuringFallback() {
        // Enforce External API Failure to trigger fallback
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500)); // Retry 3x

        // Stop RabbitMQ
        rabbit.stop();

        try {
            InternalRequest request = new InternalRequest(
                    "CHAOS-RABBIT-001",
                    new BigDecimal("200.00"),
                    "EUR",
                    "PAYMENT",
                    LocalDateTime.now());

            webTestClient.post()
                    .uri("/api/middleware/v1/transaction")
                    .header("X-API-KEY", "test-api-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().is5xxServerError();

        } finally {
            rabbit.start();
        }
    }
}
