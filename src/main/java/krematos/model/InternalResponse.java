package krematos.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InternalResponse {
    // Status pro interní klienty (true = OK, false = chyba/selhání transakce)
    private Boolean success;

    // Krátký popis stavu, nebo chybová zpráva
    private String message;

    // Unikátní identifikátor, pod kterým je transakce uložena interně
    private String internalReferenceId;
}
