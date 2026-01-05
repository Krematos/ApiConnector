package krematos.scheduler;

import krematos.model.TransactionAudit;
import krematos.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionCleanupSchedular {

    private final TransactionRepository transactionRepository;

    @Scheduled(cron = "0 * * * * *") // Každý den ve 2:00 ráno
    @SchedulerLock(name = "cleanupOldPendingTransactions", lockAtLeastFor = "15s", lockAtMostFor = "30s")
    public void cleanupOldPendingTransactions() {
        Mono.defer(() -> {
            Instant cutoffTime = Instant.now().minusSeconds(24 * 60 * 60); // 24 hodin zpět
            return transactionRepository.findByStatusAndCreatedAtBefore("PENDING", cutoffTime)
                    .flatMap(audit -> {
                        log.info("Mazání staré PENDING transakce: ID={}, internalOrderId={}", audit.getId(), audit.getInternalOrderId());
                        return transactionRepository.delete(audit);
                    })
                    .then();
        }).doOnError(error -> log.error("Chyba při čištění starých PENDING transakcí", error))
          .subscribe();
    }

    private Mono<TransactionAudit> processStuckTransaction(TransactionAudit audit) {
        log.warn("🧹 Nalezena zaseknutá transakce ID: {} (vytvořena: {}). Označuji jako FAILED.",
                audit.getInternalOrderId(), audit.getCreatedAt());

        audit.setStatus("FAILED");
        audit.setDetails("Timeout - automaticky zrušeno schedulerem");
        audit.setUpdatedAt(Instant.now());

        return transactionRepository.save(audit);
    }

}
