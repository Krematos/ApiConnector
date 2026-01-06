package krematos.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("transaction_audit")
public class TransactionAudit {
    @Id
    private Long id;
    private String internalOrderId;
    private BigDecimal amount;
    private String serviceType;
    private String currency;
    private String status;
    private String details;
    private Instant createdAt;
    private Instant updatedAt;

}
