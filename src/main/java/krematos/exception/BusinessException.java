package krematos.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Abstraktní základní třída pro všechny business výjimky
 * Poskytuje společnou funkcionalitu jako HTTP status, error code a reference ID
 */
@Getter
public abstract class BusinessException extends RuntimeException {

    /**
     * HTTP status kód který má být vrácen klientovi
     */
    private final HttpStatus httpStatus;

    /**
     * Kód chyby pro API dokumentaci a identifikaci typu chyby
     */
    private final String errorCode;

    /**
     * Business reference ID (např. ID transakce) pokud existuje
     */
    private final String referenceId;

    /**
     * Detailní zpráva pro debugging (viditelná pouze v dev prostředí)
     */
    private final String detailMessage;

    /**
     * Konstruktor s plnou specifikací
     * 
     * @param message       uživatelsky přívětivá zpráva
     * @param detailMessage detailní zpráva pro debugging
     * @param httpStatus    HTTP status kód
     * @param errorCode     kód chyby
     * @param referenceId   business reference ID
     * @param cause         původní příčina výjimky
     */
    protected BusinessException(
            String message,
            String detailMessage,
            HttpStatus httpStatus,
            String errorCode,
            String referenceId,
            Throwable cause) {
        super(message, cause);
        this.detailMessage = detailMessage;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.referenceId = referenceId;
    }

    /**
     * Zjednodušený konstruktor bez cause
     */
    protected BusinessException(
            String message,
            String detailMessage,
            HttpStatus httpStatus,
            String errorCode,
            String referenceId) {
        this(message, detailMessage, httpStatus, errorCode, referenceId, null);
    }

    /**
     * Minimální konstruktor pro jednoduché použití
     */
    protected BusinessException(String message, HttpStatus httpStatus, String errorCode) {
        this(message, null, httpStatus, errorCode, null, null);
    }
}
