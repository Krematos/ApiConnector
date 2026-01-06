package krematos.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalApiRequest {
    private String transactionId;
    private BigDecimal amount;
    private String currency;
}
