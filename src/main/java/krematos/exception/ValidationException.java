package krematos.exception;

import org.springframework.http.HttpStatus;

/**
 * Výjimka pro validační chyby vstupních dat
 * Používá se když klient pošle neplatná nebo neúplná data
 */
public class ValidationException extends BusinessException {

    private static final String ERROR_CODE = "VALIDATION_ERROR";

    /**
     * Konstruktor s chybovou zprávou
     * 
     * @param message popis validační chyby
     */
    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    /**
     * Konstruktor s chybovou zprávou a detaily
     * 
     * @param message       uživatelsky přívětivá zpráva
     * @param detailMessage detaily pro debugging
     */
    public ValidationException(String message, String detailMessage) {
        super(message, detailMessage, HttpStatus.BAD_REQUEST, ERROR_CODE, null);
    }

    /**
     * Konstruktor s reference ID
     * 
     * @param message     chybová zpráva
     * @param referenceId reference ID
     */
    public ValidationException(String message, String referenceId, String detailMessage) {
        super(message, detailMessage, HttpStatus.BAD_REQUEST, ERROR_CODE, referenceId);
    }
}
