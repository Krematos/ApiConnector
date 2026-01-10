package krematos.consumer;

import krematos.connector.ExternalSystemConnector;
import krematos.model.ExternalApiRequest;
import krematos.model.TransactionAudit;
import krematos.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailedTransactionRescuer {

    private final TransactionRepository transactionRepository;
    private final ExternalSystemConnector externalSystemConnector;

    // Spouští se každou minutu
    @Scheduled(fixedDelay = 60000)
    public void rescueStuckTransactions() {
        Instant limit = Instant.now().minus(1, ChronoUnit.MINUTES);
        log.debug("RESCUER: Hledám zaseklé transakce starší než {}", limit);

        transactionRepository.findStuckFailedTransactions(limit)
                .flatMap(this::processRescue)
                .subscribe(count -> log.info("✅ KONEC: Úspěšně zachráněno/zpracováno {} transakcí.", count),
                        error -> log.error("❌ CRITICAL: Celý proces záchrany selhal na neočekávané chybě!", error)); // V Scheduled metodě musí zavolat subscribe(), jinak se Reactive Stream nespustí!
    }

    private Mono<Void> processRescue(TransactionAudit audit) {
        log.info("RESCUER: Nalezena zaseklá transakce ID: {}", audit.getInternalOrderId());

        // Mapování Audit -> ExternalApiRequest
        ExternalApiRequest request = new ExternalApiRequest(
                audit.getInternalOrderId(),
                audit.getAmount(),
                audit.getCurrency()
        );

        // 1. Pošle do DLQ (používá public metodu z Connectoru)
        return externalSystemConnector.sendToDeadLetter(request)
                .then(transactionRepository.markAsNotified(audit.getId())) // 2. Pokud OK, označí v DB
                .doOnSuccess(v -> log.info("RESCUER: Transakce {} úspěšně zachráněna a odeslána do DLQ.", audit.getInternalOrderId()))
                .doOnError(e -> log.error("RESCUER: Nepodařilo se zachránit transakci {}: {}", audit.getInternalOrderId(), e.getMessage()))
                .onErrorResume(e -> Mono.empty()); // Chybu "spolkne", aby neshodila celý stream
    }

}
