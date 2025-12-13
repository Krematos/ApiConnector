package krematos.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InternalRequest {
    // Interní unikátní ID objednávky/transakce
    private String internalOrderId;

    // Částka, používáme BigDecimal pro přesné finanční operace
    private BigDecimal amount;

    // Kód měny (např. "CZK", "EUR")
    private String currencyCode;

    // Typ služby/produktu, který má být zpracován externě
    private String serviceType;

    // Datum a čas vytvoření požadavku
    private LocalDateTime requestedAt;
}
