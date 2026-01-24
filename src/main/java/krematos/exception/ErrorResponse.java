package krematos.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;

/**
 * Standardní error response pro všechny API chyby
 * Používá se jako fallback když BusinessException není k dispozici
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
                Instant timestamp,
                int status,
                String error,
                String message,
                String details,
                String path,
                String traceId,
                String errorCode) {
}
