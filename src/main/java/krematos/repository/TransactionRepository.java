package krematos.repository;

import krematos.model.TransactionAudit;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

import java.time.Instant;

public interface TransactionRepository extends R2dbcRepository<TransactionAudit, Long> {
    Flux<TransactionAudit> findByInternalOrderId(String internalOrderId);

    Flux<TransactionAudit> findByStatusAndCreatedAtBefore(String status, Instant before);
}
