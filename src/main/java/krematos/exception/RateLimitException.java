package krematos.exception;

import org.springframework.http.HttpStatus;

/**
 * Výjimka pro případy kdy je překročen rate limit
 * Používá se když klient posílá příliš mnoho požadavků
 */
public class RateLimitException extends BusinessException {

    private static final String ERROR_CODE = "RATE_LIMIT_EXCEEDED";

    /**
     * Doba v sekundách za jak dlouho může klient zkusit znovu
     */
    private final Long retryAfterSeconds;

    /**
     * Konstruktor s retry-after časem
     * 
     * @param message           chybová zpráva
     * @param retryAfterSeconds doba do dalšího pokusu v sekundách
     */
    public RateLimitException(String message, Long retryAfterSeconds) {
        super(
                message,
                String.format("Rate limit exceeded. Retry after %d seconds", retryAfterSeconds),
                HttpStatus.TOO_MANY_REQUESTS,
                ERROR_CODE,
                null);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Konstruktor bez retry-after času
     * 
     * @param message chybová zpráva
     */
    public RateLimitException(String message) {
        this(message, null);
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
