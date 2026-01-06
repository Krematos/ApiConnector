package krematos.connector;

import krematos.model.ExternalApiRequest;
import krematos.model.ExternalApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ExternalSystemConnector {

    @Autowired
        private RabbitTemplate rabbitTemplate;
        private final WebClient webClient;

        public ExternalSystemConnector(WebClient.Builder webClientBuilder,
                        @Value("${external.api.base-url}") String baseUrl) {
                this.webClient = webClientBuilder
                                .baseUrl(baseUrl)
                                .defaultHeaders(headers -> {
                                        headers.add("Content-Type", "application/json");
                                        headers.add("Accept", "application/json");
                                        headers.add("User-Agent", "Krematos-Middleware-Connector/1.0");
                                        headers.setBearerAuth("your-api-token-here"); // Pokud je potřeba autentizace
                                })
                                .build();
        }

        /**
         * Zavolá externí API s automatickým opakováním při selhání sítě nebo 5xx chybě.
         * 
         * @Retryable zajistí automatické opakování pokusu (vyžaduje
         *            anotaci @EnableRetry v hlavní třídě)
         */
        @Retryable(retryFor = { WebClientResponseException.ServiceUnavailable.class, // 503
                        WebClientResponseException.GatewayTimeout.class, // 504
                        WebClientResponseException.InternalServerError.class }, // 500
                        maxAttemptsExpression = "${connector.retry.max-attempts:3}", backoff = @Backoff(delayExpression = "${connector.retry.delay-ms:1000}"))
        public Mono<ExternalApiResponse> sendRequest(ExternalApiRequest request) {
                log.info("Volání externího API pro transakci: {}", request.getTransactionId());

                return webClient.post()
                                .uri("/v1/process") // Koncový bod externího API
                                .body(Mono.just(request), ExternalApiRequest.class)
                                .retrieve()

                                // Logika pro zpracování stavových kódů:
                                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                                        // Převod 4xx chyb (které se neopakují) na vlastní výjimku
                                        return clientResponse.bodyToMono(String.class)
                                                        .flatMap(errorBody -> {
                                                                log.error("Chyba klienta při volání externího API: {} - {}",
                                                                                clientResponse.statusCode(), errorBody);
                                                                return Mono.error(new ExternalApiException(
                                                                                "Chybný požadavek na externí API: "
                                                                                                + clientResponse.statusCode(),
                                                                                request.getTransactionId()));
                                                        });
                                })
                                .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                                        // 5xx chyby se automaticky opakují
                                        // Zde stačí vrátit standardní chybu WebClient, která vyvolá opakování.
                                        return Mono.error(WebClientResponseException.create(
                                                        clientResponse.statusCode().value(),
                                                        "Externí server selhal.",
                                                        null, null, null));
                                })

                                // Mapování těla odpovědi na náš DTO model
                                .bodyToMono(ExternalApiResponse.class)

                                .doOnSuccess(response -> log.info(
                                                "-> Externí volání: Úspěšná odpověď pro transakci: {}",
                                                request.getTransactionId()))

                                // Logika, která se spustí po všech neúspěšných pokusech
                                .onErrorResume(throwable -> {
                                    log.error("Externí volání selhalo po všech pokusech: {}", throwable.getMessage());

                                    // Fallback: Odeslání do RabbitMQ a vrácení chyby/odpovědi
                                    return Mono.fromRunnable(() -> {
                                                log.info("Odesílám transakci {} do záložní fronty.", request.getTransactionId());
                                                // Parametry: (exchange, routingKey, objekt)
                                                rabbitTemplate.convertAndSend("failed.transactions.exchange", "retry.key", request);
                                            })
                                            .then(Mono.error(new RuntimeException(
                                                    "API konektor selhal. Transakce byla uložena do fronty k pozdějšímu zpracování.",
                                                    throwable)));

                                });
        }
}
