package krematos.service;

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
     */
    public Mono<InternalResponse> process(InternalRequest request) {
        log.info("--- ZAČÁTEK TRANSAKCE: {} ---", request.getInternalOrderId());

        return createPendingAudit(request) // Krok 1: Audit (PENDING)
                .flatMap(audit -> callExternalSystem(request) // Krok 2: Volání API
                        .flatMap(response -> handleSuccess(audit, response, request)) // Krok 3a: Úspěch
                        .onErrorResume(error -> handleFailure(audit, error))          // Krok 3b: Chyba
                );
    }

    // --- KROK 1: Vytvoření záznamu v DB ---
    private Mono<TransactionAudit> createPendingAudit(InternalRequest request) {
        TransactionAudit audit = TransactionAudit.builder()
                .internalOrderId(request.getInternalOrderId())
                .amount(request.getAmount())
                .currency(request.getCurrencyCode())
                .serviceType(request.getServiceType())
                .status("PENDING") // Důležité: Začíná jako PENDING
                .createdAt(Instant.now())
                .build();

        return transactionRepository.save(audit)
                .doOnSuccess(a -> log.debug("Audit uložen: PENDING (ID: {})", a.getId()));
    }

    // --- KROK 2: Volání externího systému ---
    private Mono<ExternalApiResponse> callExternalSystem(InternalRequest request) {
        ExternalApiRequest externalRequest = mapToExternal(request);
        return externalSystemConnector.sendRequest(externalRequest)
                .doOnSuccess(response -> log.debug("Externí API volání úspěšné pro transakci: {}", request.getInternalOrderId()));
    }

    // --- KROK 3a: Zpracování úspěchu ---
    private Mono<InternalResponse> handleSuccess(TransactionAudit audit, ExternalApiResponse response, InternalRequest request) {
        audit.setStatus("SUCCESS");
        audit.setDetails("Potvrzeno ID: " + response.getConfirmationId());
        audit.setUpdatedAt(Instant.now());

        return transactionRepository.save(audit)
                .doOnSuccess(a -> log.info("Audit aktualizován: SUCCESS"))
                .map(saved -> mapToInternal(response, request.getInternalOrderId()));
    }

    // --- KROK 3b: Zpracování chyby ---
    private Mono<InternalResponse> handleFailure(TransactionAudit audit, Throwable error) {
        audit.setStatus("FAILED");
        audit.setDetails(error.getMessage());
        audit.setUpdatedAt(Instant.now());

        // Nejdřív uloží FAILED do DB, a až PAK pošle chybu dál (aby ji chytil Controller nebo RabbitMQ)
        return transactionRepository.save(audit)
                .doOnSuccess(a -> log.error("Audit aktualizován: FAILED ({})", error.getMessage()))
                .then(Mono.error(error));
    }

    // --- Mappery (pomocné metody) ---

    private ExternalApiRequest mapToExternal(InternalRequest internal) {
        // Pozor: Zde musí převést BigDecimal na double, protože ExternalApiRequest má double
        return new ExternalApiRequest(
                internal.getInternalOrderId(),
                internal.getAmount().doubleValue(), // <--- Převod
                internal.getCurrencyCode()
        );
    }

    private InternalResponse mapToInternal(ExternalApiResponse external, String orderId) {
        return new InternalResponse(true, "OK: " + external.getDetailStatus(), orderId);
    }
}
