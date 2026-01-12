package krematos.connector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import krematos.exception.ExternalServiceException;
import krematos.model.ExternalApiRequest;
import krematos.model.ExternalApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Konektor pro komunikaci s externím platebním systémem
 * Obsahuje logiku pro:
 * - OAuth2 autentizaci
 * - Exponenciální retry s backoff
 * - Odesílání chybných requestů do Dead Letter Queue (DLQ)
 */
@Slf4j
@Component
public class ExternalSystemConnector {

        private final WebClient webClient;
        private final Sender rabbitSender; // Neblokující Sender (reactor-rabbitmq)
        private final ObjectMapper objectMapper; // Pro serializaci JSONu

        // Konstanty pro Retry logiku
        private static final int MAX_ATTEMPTS = 3;
        private static final int RETRY_DELAY_MS = 1000;
        private static final String SERVICE_NAME = "External Payment API";

        /**
         * Pomocná metoda pro filtrování chyb k opakování.
         */
        /**
         * Constructor Injection.
         * Spring sem automaticky injektuje bean "externalSystemWebClient" z
         * WebClientConfigu,
         * protože typ sedí. Pokud bys měl WebClientů víc, musel bys použít @Qualifier.
         */
        public ExternalSystemConnector(
                        WebClient.Builder webClientBuilder,
                        ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
                        Sender rabbitSender,
                        ObjectMapper objectMapper,
                        @Value("${external.api.base-url}") String baseUrl) {
                this.rabbitSender = rabbitSender;
                this.objectMapper = objectMapper;

                // 3. Vytvoření filtru pro OAuth2
                ServerOAuth2AuthorizedClientExchangeFilterFunction oauth = new ServerOAuth2AuthorizedClientExchangeFilterFunction(
                                authorizedClientManager);

                // 4. Nastavení, který klient se má použít (odpovídá application.yml)
                oauth.setDefaultClientRegistrationId("external-api-client");

                this.webClient = webClientBuilder
                                .baseUrl(baseUrl)
                                .filter(oauth) // <--- 5. Přidání filtru do WebClienta
                                .defaultHeader("Content-Type", "application/json")
                                .build();
        }

        public Mono<ExternalApiResponse> sendRequest(ExternalApiRequest request) {
                log.info("Volání externího API pro transakci: {}", request.getTransactionId());

                return webClient.post()
                                .uri("/v1/process") // Už jen relativní cesta, BaseURL je v klientovi
                                .bodyValue(request)
                                .retrieve()
                                // Zpracování 4xx chyb (chyba na naší straně - špatný request)
                                .onStatus(HttpStatusCode::is4xxClientError,
                                                clientResponse -> clientResponse.bodyToMono(String.class)
                                                                .flatMap(errorBody -> {
                                                                        log.error("Chyba klienta (4xx): {} - {}",
                                                                                        clientResponse.statusCode(),
                                                                                        errorBody);
                                                                        return Mono.error(new ExternalServiceException(
                                                                                        "Chybný požadavek do externího systému: "
                                                                                                        + clientResponse.statusCode(),
                                                                                        SERVICE_NAME,
                                                                                        clientResponse.statusCode()
                                                                                                        .value(),
                                                                                        request.getTransactionId(),
                                                                                        errorBody,
                                                                                        null));
                                                                }))
                                // Zpracování 5xx chyb (chyba na straně externího serveru)
                                .onStatus(HttpStatusCode::is5xxServerError,
                                                clientResponse -> Mono.error(WebClientResponseException.create(
                                                                clientResponse.statusCode().value(),
                                                                "Externí server selhal.", null, null, null)))
                                .bodyToMono(ExternalApiResponse.class)

                                // --- REACTIVE RETRY s exponenciálním backoff ---
                                // Opakujeme pouze dočasné chyby (5xx, timeouty, connection errors)
                                .retryWhen(Retry.backoff(MAX_ATTEMPTS, Duration.ofMillis(RETRY_DELAY_MS))
                                                .filter(this::isRetryable)
                                                .doBeforeRetry(retrySignal -> log.warn(
                                                                "Opakuji volání (pokus {}/{}). Chyba: {}",
                                                                retrySignal.totalRetries() + 1, MAX_ATTEMPTS,
                                                                retrySignal.failure().getMessage())))

                                .doOnSuccess(response -> log.info("-> Externí volání OK: {}",
                                                request.getTransactionId()))

                                // --- FALLBACK (DLQ) ---
                                // Pokud všechny pokusy selhaly, odešleme zprávu do Dead Letter Queue
                                .onErrorResume(throwable -> {
                                        log.error("Externí volání selhalo po všech pokusech: {}",
                                                        throwable.getMessage());
                                        return sendToDeadLetter(request)
                                                        .then(Mono.error(new ExternalServiceException(
                                                                        "Externí služba není dostupná po "
                                                                                        + MAX_ATTEMPTS
                                                                                        + " pokusech. Požadavek uložen do DLQ.",
                                                                        SERVICE_NAME,
                                                                        request.getTransactionId(),
                                                                        throwable)));
                                });
        }

        /**
         * Určuje zda chyba je dočasná a má smysl ji opakovat
         * Opakujeme pouze:
         * - 503 Service Unavailable (server je dočasně nedostupný)
         * - 504 Gateway Timeout (timeout při proxy/gateway)
         * - 500 Internal Server Error (může být dočasná chyba serveru)
         * - Connection errors (síťové problémy)
         * 
         * NEOPAKUJEME 4xx chyby - ty indikují problém v našem požadavku
         */
        private boolean isRetryable(Throwable ex) {
                return ex instanceof WebClientResponseException.ServiceUnavailable ||
                                ex instanceof WebClientResponseException.GatewayTimeout ||
                                ex instanceof WebClientResponseException.InternalServerError ||
                                ex instanceof java.net.ConnectException;
        }

        /**
         * Odešle chybný request do RabbitMQ Dead Letter Queue pro pozdější zpracování
         * Používá se když externí API selže po všech retry pokusech
         */
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
                                                jsonBytes))
                                .flatMap(msg -> rabbitSender.send(Mono.just(msg)))
                                .doOnSuccess(v -> log.info("Zpráva úspěšně odložena do RabbitMQ DLQ"))
                                .doOnError(e -> log.error("CRITICAL: Nepodařilo se zapsat do RabbitMQ!", e))
                                .onErrorResume(e -> Mono.empty())
                                .then();
        }
}
