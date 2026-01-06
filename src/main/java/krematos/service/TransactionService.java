package krematos.service;

import krematos.connector.ExternalApiException;
import krematos.connector.ExternalSystemConnector;
import krematos.model.*;
import krematos.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;


@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final ExternalSystemConnector externalSystemConnector;
    private final TransactionRepository transactionRepository;



    /**
     * Hlavní "Orchestrátor".
     * čitelný tok: Ulož -> Zavolej -> Aktualizuj (nebo zpracuj chybu).
     * rozložen do kroků pro lepší srozumitelnost a údržbu.
     */
    public Mono<InternalResponse> process(InternalRequest request) {
        log.info("--- ZAČÁTEK TRANSAKCE: {} ---", request.getInternalOrderId());

        return validateRequest(request) // Krok 1: Audit (PENDING)
                .flatMap(this::createPendingAudit) // Krok 1: Vytvoření záznamu v DB
                .flatMap(audit -> timedExternalCall(request) // Krok 2: Měření latence externího API
                        .switchIfEmpty(Mono.error(new ExternalApiException("Prázdná odpověď od externího systému", request.getInternalOrderId()))) // switchIfEmpty pro prázdnou odpověď
                        .flatMap(response -> handleSuccess(audit, response, request)) // Krok 3a: Úspěch
                        .onErrorResume(error -> handleFailure(audit, error))          // Krok 3b: Chyba
                );
    }

    // --- Předběžná validace požadavku ---
    private Mono<InternalRequest> validateRequest(InternalRequest request) {
        if (request.getAmount() == null || request.getAmount().doubleValue() <= 0) {
            log.warn("Neplatná částka: {}", request.getAmount());
            return Mono.error(new ExternalApiException("Neplatná částka v požadavku", request.getInternalOrderId()));
        }
        if (request.getCurrencyCode() == null || request.getCurrencyCode().isEmpty()) {
            log.warn("Chybějící měnový kód");
            return Mono.error(new ExternalApiException("Chybějící měnový kód v požadavku", request.getInternalOrderId()));
        }

        return Mono.just(request);
    }

    // --- KROK 1: Vytvoření záznamu v DB ---
    private Mono<TransactionAudit> createPendingAudit(InternalRequest request) {
        TransactionAudit audit = TransactionAudit.builder()
                .internalOrderId(request.getInternalOrderId())
                .amount(request.getAmount())
                .currency(request.getCurrencyCode())
                .serviceType(request.getServiceType())
                .status(AuditStatus.PENDING.name()) // Důležité: Začíná jako PENDING
                .createdAt(Instant.now())
                .build();

        return transactionRepository.save(audit)
                .doOnSuccess(a -> log.debug("Audit uložen: PENDING (ID: {})", a.getId()));
    }

    // --- KROK 2: Volání externího systému ---


    // --- KROK 2: Měření latence externího volání
    private Mono<ExternalApiResponse> timedExternalCall(InternalRequest request) {
        ExternalApiRequest externalRequest = mapToExternal(request);
        Instant startTime = Instant.now();
        return externalSystemConnector.sendRequest(externalRequest)
                .doOnSuccess(resp -> log.info("Externí volání dokončeno za {} ms",
                        Instant.now().toEpochMilli() - startTime.toEpochMilli()))
                .doOnError(error -> log.error("Chyba při externím volání po {} ms: {}",
                        Instant.now().toEpochMilli() - startTime.toEpochMilli(), error.getMessage()));
    }

    // --- KROK 3a: Zpracování úspěchu ---
    private Mono<InternalResponse> handleSuccess(TransactionAudit audit, ExternalApiResponse response, InternalRequest request) {
        audit.setStatus(AuditStatus.SUCCESS.name());
        audit.setDetails("Potvrzeno ID: " + response.getConfirmationId());
        audit.setUpdatedAt(Instant.now());

        return transactionRepository.save(audit)
                .doOnSuccess(a -> log.info("Audit aktualizován: SUCCESS"))
                .map(saved -> mapToInternal(response, request.getInternalOrderId()));
    }

    // --- KROK 3b: Zpracování chyby ---
    private Mono<InternalResponse> handleFailure(TransactionAudit audit, Throwable error) {
        audit.setStatus(AuditStatus.FAILED.name());
        audit.setDetails(error.getMessage());
        audit.setUpdatedAt(Instant.now());

        // Nejdřív uloží FAILED do DB, a až PAK pošle chybu dál (aby ji chytil Controller nebo RabbitMQ)
        return transactionRepository.save(audit)
                .doOnSuccess(a -> log.error("Audit aktualizován: FAILED ({})", error.getMessage()))
                .then(Mono.error(error));
    }

    // --- Mappery (pomocné metody) ---

    private ExternalApiRequest mapToExternal(InternalRequest internal) {
        return new ExternalApiRequest(
                internal.getInternalOrderId(),
                internal.getAmount(),
                internal.getCurrencyCode()
        );
    }

    private InternalResponse mapToInternal(ExternalApiResponse external, String orderId) {
        return new InternalResponse(true, "OK: " + external.getDetailStatus(), orderId);
    }
}
