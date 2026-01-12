package krematos.connector;

import krematos.model.ExternalApiRequest;
import krematos.model.ExternalApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

// 1. DEFINED_PORT je kritické!
// Donutí test nastartovat skutečný server na portu 8080 (nebo tom z yml),
// aby si Connector mohl sáhnout na localhost:8080.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
// 2. Aktivuje profil, pokud je oddělený config (např. application-local.yml)
@ActiveProfiles("local")
class ExternalSystemConnectorIntegrationTest {

    @Autowired
    private ExternalSystemConnector connector;

    @Test
    @DisplayName("Connector by měl získat Token a úspěšně zavolat Mock API")
    void shouldAuthenticateAndProcessTransaction() {
        // A. Příprava dat
        ExternalApiRequest request = new ExternalApiRequest();
        request.setTransactionId("INTEGRATION-TEST-001");
        request.setAmount(new BigDecimal("1000.00"));
        request.setCurrency("CZK");

        // B. Volání metody (která interně dělá OAuth + API call)
        // Používá StepVerifier, protože vrací reaktivní Mono
        StepVerifier.create(connector.sendRequest(request))

                // C. Ověření
                .expectNextMatches(response -> {
                    System.out.println(">>> PŘIJATÁ ODPOVĚĎ Z MOCKU: " + response);

                    // Ověří, že jsme dostali správnou odpověď z MockExternalController
                    return response.getStatusCode() == 200
                            && response.getDetailStatus().equals("COMPLETED") // Podle  Mocku
                            && response.getConfirmationId() != null;
                })
                .expectComplete()
                .verify(); // Spustí test
    }
}
