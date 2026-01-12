package krematos.exception;

import org.springframework.http.HttpStatus;

/**
 * Výjimka pro chyby při komunikaci s externími službami
 * Používá se když externí API/služba vrátí chybu nebo není dostupná
 */
public class ExternalServiceException extends BusinessException {

    private static final String ERROR_CODE = "EXTERNAL_SERVICE_ERROR";

    /**
     * HTTP status kód vrácený externí službou
     */
    private final Integer externalStatusCode;

    /**
     * Název externí služby která selhala
     */
    private final String serviceName;

    /**
     * Konstruktor s plnou specifikací
     * 
     * @param message            uživatelsky přívětivá zpráva
     * @param serviceName        název externí služby
     * @param externalStatusCode HTTP status vrácený externí službou
     * @param referenceId        reference ID transakce
     * @param detailMessage      detaily pro debugging (např. původní error z API)
     * @param cause              původní výjimka
     */
    public ExternalServiceException(
            String message,
            String serviceName,
            Integer externalStatusCode,
            String referenceId,
            String detailMessage,
            Throwable cause) {
        super(
                message,
                detailMessage,
                HttpStatus.BAD_GATEWAY, // 502 - chyba při komunikaci s externí službou
                ERROR_CODE,
                referenceId,
                cause);
        this.serviceName = serviceName;
        this.externalStatusCode = externalStatusCode;
    }

    /**
     * Zjednodušený konstruktor
     * 
     * @param message     chybová zpráva
     * @param serviceName název služby
     * @param referenceId reference ID
     */
    public ExternalServiceException(String message, String serviceName, String referenceId) {
        this(message, serviceName, null, referenceId, null, null);
    }

    /**
     * Konstruktor s původní výjimkou
     * 
     * @param message     chybová zpráva
     * @param serviceName název služby
     * @param referenceId reference ID
     * @param cause       původní výjimka
     */
    public ExternalServiceException(
            String message,
            String serviceName,
            String referenceId,
            Throwable cause) {
        this(message, serviceName, null, referenceId, cause != null ? cause.getMessage() : null, cause);
    }

    public Integer getExternalStatusCode() {
        return externalStatusCode;
    }

    public String getServiceName() {
        return serviceName;
    }
}
