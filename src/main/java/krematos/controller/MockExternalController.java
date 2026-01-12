package krematos.controller;

import krematos.model.ExternalApiRequest;
import krematos.model.ExternalApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/mock-external")
@Profile({ "test", "local" }) // Aktivní pouze v "test" a "local" profilech
public class MockExternalController {
    @PostMapping("/v1/process")
    public Mono<ExternalApiResponse> processTransaction(@RequestBody ExternalApiRequest request) {
        // Simulace zpoždění sítě (náhodně 50ms až 500ms) - to udělá hezké zuby v
        // grafech
        long delay = ThreadLocalRandom.current().nextLong(50, 500);

        return Mono.just(new ExternalApiResponse(200,
                UUID.randomUUID().toString(), // Vygeneruje fiktivní ID transakce
                "COMPLETED",
                delay))
                .delayElement(Duration.ofMillis(delay)); // Umělé zpoždění
    }
}
