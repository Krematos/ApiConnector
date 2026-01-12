package krematos.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Strukturovaná odpověď pro chybové stavy
 * Poskytuje konzistentní formát pro všechny chyby v API
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // Nezahrnovat null hodnoty v JSON odpovědi
public class ErrorResponse {

    /**
     * Časové razítko kdy došlo k chybě
     */
    private LocalDateTime timestamp;

    /**
     * HTTP status kód (např. 400, 404, 500)
     */
    private int status;

    /**
     * Název typu chyby (např. "Bad Request", "Not Found")
     */
    private String error;

    /**
     * Uživatelsky přívětivá chybová zpráva
     */
    private String message;

    /**
     * Detailní informace pro debugging (pouze v dev/local prostředí)
     */
    private String details;

    /**
     * Cesta API endpointu kde došlo k chybě (např. /api/transaction)
     */
    private String path;

    /**
     * Unikátní identifikátor pro sledování chyby v logách
     * Umožňuje snadné vyhledání konkrétního požadavku v logových souborech
     */
    private String traceId;

    /**
     * Business reference ID (např. ID transakce) pokud existuje
     * Umožňuje klientovi sledovat konkrétní business operaci
     */
    private String referenceId;

    /**
     * Kód chyby pro API dokumentaci (např. "VALIDATION_ERROR",
     * "EXTERNAL_SERVICE_ERROR")
     */
    private String errorCode;
}
