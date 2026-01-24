package krematos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;

/**
 * Standardní API error response pro business výjimky
 * Obsahuje všechny potřebné informace pro debugging a error tracking
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String details,
        String path,
        String traceId,
        String referenceId,
        String errorCode) {
}
