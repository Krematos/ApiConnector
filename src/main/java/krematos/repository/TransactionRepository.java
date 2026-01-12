package krematos.repository;

import krematos.model.TransactionAudit;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface TransactionRepository extends R2dbcRepository<TransactionAudit, Long> {

    Flux<TransactionAudit> findByStatusAndCreatedAtBefore(String status, Instant before);

    @Query("SELECT * FROM transaction_audit " +
            "WHERE status = 'FAILED' " +
            "AND notification_sent = FALSE " +
            "AND updated_at < :cutoffTime")
    Flux<TransactionAudit> findStuckFailedTransactions(Instant cutoffTime);

    @Query("UPDATE transaction_audit SET notification_sent = TRUE WHERE id = :id")
    Mono<Void> markAsNotified(Long id);

    @Query("UPDATE transaction_audit SET status = 'FAILED' AND notification_sent = false LIMIT 50")
    Flux<TransactionAudit> findFailedAnoNotNotified();

    // Pro integrační testy - vyhledání transakce podle interního ID
    Mono<TransactionAudit> findByInternalOrderId(String internalOrderId);

}
