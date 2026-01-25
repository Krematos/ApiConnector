package krematos.connector;

import krematos.model.ExternalApiRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.math.BigDecimal;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class ExternalSystemConnectorIntegrationTest {

    @Autowired
    private ExternalSystemConnector connector;
    /*
    @Test
    @DisplayName("Connector by měl získat Token a úspěšně zavolat Mock API")
    void shouldAuthenticateAndProcessTransaction() {
        // A. Příprava dat
        ExternalApiRequest request = new ExternalApiRequest();
        request.setTransactionId("INTEGRATION-TEST-001");
        request.setAmount(new BigDecimal("1000.00"));
        request.setCurrency("CZK");

        // B. Volání metody connectoru
        StepVerifier.create(connector.sendRequest(request))

                // C. Ověření
                .expectNextMatches(response -> {
                    System.out.println(">>> PŘIJATÁ ODPOVĚĎ Z MOCKU: " + response);

                    // Ověří, správnou odpověď z MockExternalController
                    return response.getStatusCode() == 200
                            && response.getDetailStatus().equals("COMPLETED") // Podle Mocku
                            && response.getConfirmationId() != null;
                })
                .expectComplete()
                .verify(); // Spustí test
    }*/
}
