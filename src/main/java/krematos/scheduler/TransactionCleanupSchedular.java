package krematos.scheduler;

import krematos.model.AuditStatus;
import krematos.model.TransactionAudit;
import krematos.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionCleanupSchedular {

    private final TransactionRepository transactionRepository;

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "cleanupOldPendingTransactions", lockAtLeastFor = "15s", lockAtMostFor = "30s")
    public void cleanupOldPendingTransactions() {
        log.info("Spouštím úklid starých PENDING transakcí...");

        Instant cutoffTime = Instant.now().minus(24, ChronoUnit.HOURS);

        transactionRepository.findByStatusAndCreatedAtBefore(AuditStatus.PENDING.name(), cutoffTime)
                .flatMap(this::processStuckTransaction)
                .doOnComplete(() -> log.info("Úklid transakcí dokončen."))
                .doOnError(error -> log.error("Chyba při čištění starých PENDING transakcí", error))
                .subscribe();
    }

    private Mono<TransactionAudit> processStuckTransaction(TransactionAudit audit) {
        log.warn("Nalezena zaseknutá transakce ID: {} (vytvořena: {}). Označuji jako FAILED.",
                audit.getInternalOrderId(), audit.getCreatedAt());

        audit.setStatus("FAILED");
        audit.setDetails("Timeout - automaticky zrušeno schedulerem");
        audit.setUpdatedAt(Instant.now());

        return transactionRepository.save(audit);
    }

}
