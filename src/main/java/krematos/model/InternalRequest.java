package krematos.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class InternalRequest {
    // Interní unikátní ID objednávky/transakce
    private String internalOrderId;

    // Částka, používá BigDecimal pro přesné finanční operace
    @NotNull(message = "amount nesmí být null")
    private BigDecimal amount;

    // Kód měny (např. "CZK", "EUR")
    @NotNull(message = "currencyCode nesmí být null")
    private String currencyCode;

    // Typ služby/produktu, který má být zpracován externě
    @NotNull(message = "serviceType nesmí být null")
    private String serviceType;

    // Datum a čas vytvoření požadavku
    @NotNull(message = "requestedAt nesmí být null")
    private Instant requestedAt;
}
