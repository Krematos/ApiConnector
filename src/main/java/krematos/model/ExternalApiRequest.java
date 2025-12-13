package krematos.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalApiRequest {
    private String transactionId;
    private double amount;
    private String currency;
}
