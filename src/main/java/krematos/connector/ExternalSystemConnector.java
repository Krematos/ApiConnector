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



    /**
     * Pomocná metoda pro filtrování chyb k opakování.
     */
    /**
     * Constructor Injection.
     * Spring sem automaticky injektuje bean "externalSystemWebClient" z WebClientConfigu,
     * protože typ sedí. Pokud bys měl WebClientů víc, musel bys použít @Qualifier.
     */
    public ExternalSystemConnector(WebClient externalSystemWebClient, // Název parametru může napovědět Springu
                                   Sender rabbitSender,
                                   ObjectMapper objectMapper) {
        this.webClient = externalSystemWebClient; // Už žádné .mutate(), bereme hotového klienta
        this.rabbitSender = rabbitSender;
        this.objectMapper = objectMapper;
    }

    public Mono<ExternalApiResponse> sendRequest(ExternalApiRequest request) {
        log.info("Volání externího API pro transakci: {}", request.getTransactionId());

        return webClient.post()
                .uri("/v1/process") // Už jen relativní cesta, BaseURL je v klientovi
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Chyba klienta (4xx): {} - {}", clientResponse.statusCode(), errorBody);
                                    return Mono.error(new ExternalApiException(
                                            "Chybný požadavek: " + clientResponse.statusCode(),
                                            request.getTransactionId()));
                                })
                )
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                        Mono.error(WebClientResponseException.create(
                                clientResponse.statusCode().value(),
                                "Externí server selhal.", null, null, null))
                )
                .bodyToMono(ExternalApiResponse.class)

                // --- REACTIVE RETRY ---
                .retryWhen(Retry.backoff(MAX_ATTEMPTS, Duration.ofMillis(RETRY_DELAY_MS))
                        .filter(this::isRetryable)
                        .doBeforeRetry(retrySignal -> log.warn("Opakuji volání (pokus {}/{}). Chyba: {}",
                                retrySignal.totalRetries() + 1, MAX_ATTEMPTS, retrySignal.failure().getMessage()))
                )

                .doOnSuccess(response -> log.info("-> Externí volání OK: {}", request.getTransactionId()))

                // --- FALLBACK (DLQ) ---
                .onErrorResume(throwable -> {
                    log.error("Externí volání selhalo po všech pokusech: {}", throwable.getMessage());
                    return sendToDeadLetter(request)
                            .then(Mono.error(new RuntimeException("API selhalo, odesláno do DLQ.", throwable)));
                });
    }

    private boolean isRetryable(Throwable ex) {
        return ex instanceof WebClientResponseException.ServiceUnavailable ||
                ex instanceof WebClientResponseException.GatewayTimeout ||
                ex instanceof WebClientResponseException.InternalServerError ||
                ex instanceof java.net.ConnectException;
    }

    public Mono<Void> sendToDeadLetter(ExternalApiRequest request) {
        return Mono.fromCallable(() -> {
                    try {
                        return objectMapper.writeValueAsBytes(request);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Chyba serializace pro DLQ", e);
                    }
                })
                .map(jsonBytes -> new OutboundMessage(
                        "failed.transactions.exchange",
                        "retry.key",
                        jsonBytes
                ))
                .flatMap(msg -> rabbitSender.send(Mono.just(msg)))
                .doOnSuccess(v -> log.info("Zpráva úspěšně odložena do RabbitMQ DLQ"))
                .doOnError(e -> log.error("CRITICAL: Nepodařilo se zapsat do RabbitMQ!", e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}
