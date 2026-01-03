package krematos.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalApiResponse {

    // Příklad, kde se externí API vrací "status_code" a my ho mapuje na "statusCode"
    @JsonProperty("status_code")
    private int statusCode;

    // Potvrzovací ID transakce generované externím systémem
    @JsonProperty("confirmation_id")
    private String confirmationId;

    // Podrobný stav, např. "COMPLETED", "FAILED", "PENDING"
    private String detailStatus;

    // Volitelné: Čas zpracování na straně externího systému
    private Long processingTimeMs;
}
