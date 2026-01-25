package krematos.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/mock-auth")
@Profile({ "test", "local" })
public class MockAuthController {
    /**
     * Simuluje OAuth2 Token Endpoint (např. Keycloak /token).
     * Spring Security sem pošle POST request s grant_type=client_credentials.
     */
    @PostMapping("/token")
    public Mono<Map<String, Object>> getToken() {
        Map<String, Object> tokenResponse = new HashMap<>();

        // Vrátí "falešný" token
        tokenResponse.put("access_token", "mock-jwt-token-" + Instant.now().toEpochMilli());
        tokenResponse.put("token_type", "Bearer");
        tokenResponse.put("expires_in", 3600); // Platnost 1 hodina
        tokenResponse.put("scope", "write");

        return Mono.just(tokenResponse);
    }
}
