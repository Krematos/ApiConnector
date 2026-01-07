package krematos.connector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import krematos.model.ExternalApiRequest;
import krematos.model.ExternalApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Component
public class ExternalSystemConnector {

    private final WebClient webClient;
    private final Sender rabbitSender; // Neblokující Sender (reactor-rabbitmq)
    private final ObjectMapper objectMapper; // Pro serializaci JSONu

    // Konstanty pro Retry logiku (lze vytáhnout do @Value)
    private static final int MAX_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 1000;

    // Constructor Injection (nejlepší praxe podle Gemini )
    public ExternalSystemConnector(WebClient externalWebClient,
                                   Sender rabbitSender,
                                   ObjectMapper objectMapper,
                                   @Value("${external.api.base-url}") String baseUrl) {
        this.rabbitSender = rabbitSender;
        this.objectMapper = objectMapper;
        this.webClient = externalWebClient.mutate()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> {
                    headers.add("Content-Type", "application/json");
                    headers.add("Accept", "application/json");
                    headers.add("User-Agent", "Krematos-Middleware-Connector/1.0");
                })
                .build();
    }

    /**
     * Zavolá externí API.
     * Používá .retryWhen() pro neblokující opakování.
     */
    public Mono<ExternalApiResponse> sendRequest(ExternalApiRequest request) {
        log.info("Volání externího API pro transakci: {}", request.getTransactionId());

        return webClient.post()
                .uri("/v1/process")
                .bodyValue(request) // .bodyValue je efektivnější pro hotové objekty než Mono.just
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Chyba klienta (4xx): {} - {}", clientResponse.statusCode(), errorBody);
                                    // 4xx chyby neopakujeme, vracíme rovnou chybu
                                    return Mono.error(new ExternalApiException(
                                            "Chybný požadavek: " + clientResponse.statusCode(),
                                            request.getTransactionId()));
                                })
                )
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                        // 5xx chyby vyhodí jako Exception, aby je zachytil .retryWhen() níže
                        Mono.error(WebClientResponseException.create(
                                clientResponse.statusCode().value(),
                                "Externí server selhal.", null, null, null))
                )
                .bodyToMono(ExternalApiResponse.class)

                // --- REACTIVE RETRY LOGIKA ---
                .retryWhen(Retry.backoff(MAX_ATTEMPTS, Duration.ofMillis(RETRY_DELAY_MS))
                        .filter(throwable -> isRetryable(throwable)) // Filtrujeme, co chceme opakovat
                        .doBeforeRetry(retrySignal -> log.warn("Opakuji volání (pokus {}/{}). Chyba: {}",
                                retrySignal.totalRetries() + 1, MAX_ATTEMPTS, retrySignal.failure().getMessage()))
                )

                .doOnSuccess(response -> log.info("-> Externí volání OK: {}", request.getTransactionId()))

                // --- FALLBACK (Dead Letter Queue) ---
                .onErrorResume(throwable -> {
                    log.error("Externí volání selhalo po všech pokusech: {}", throwable.getMessage());

                    // Pokusí se odeslat do RabbitMQ a pak vyhodí chybu dál (aby Service věděl, že to selhalo)
                    return sendToDeadLetter(request)
                            .then(Mono.error(new RuntimeException("API selhalo, odesláno do DLQ.", throwable)));
                });
    }

    /**
     * Pomocná metoda pro filtrování chyb k opakování.
     */
    private boolean isRetryable(Throwable ex) {
        return ex instanceof WebClientResponseException.ServiceUnavailable || // 503
                ex instanceof WebClientResponseException.GatewayTimeout ||     // 504
                ex instanceof WebClientResponseException.InternalServerError || // 500
                ex instanceof java.net.ConnectException; // Chyba sítě
    }

    /**
     * Neblokující odeslání do RabbitMQ pomocí reactor-rabbitmq Sender.
     */
    public Mono<Void> sendToDeadLetter(ExternalApiRequest request) {
        return Mono.fromCallable(() -> {
                    // 1. Serializace na JSON (může blokovat CPU, proto fromCallable)
                    try {
                        return objectMapper.writeValueAsBytes(request);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Chyba serializace pro DLQ", e);
                    }
                })
                .map(jsonBytes -> new OutboundMessage(
                        "failed.transactions.exchange", // Exchange
                        "retry.key",                   // Routing Key
                        jsonBytes                      // Data
                ))
                .flatMap(msg -> rabbitSender.send(Mono.just(msg))) // 2. Odeslání
                .doOnSuccess(v -> log.info("Zpráva úspěšně odložena do RabbitMQ DLQ"))
                .doOnError(e -> log.error("CRITICAL: Nepodařilo se zapsat do RabbitMQ! Zpráva je pouze v DB (status FAILED).", e))
                .onErrorResume(e -> Mono.empty()) // Ignoruje chybu Rabbitu, aby aplikace nespadla (data jsou v DB)
                .then();
    }
}
