package krematos.exception;

import org.springframework.http.HttpStatus;

/**
 * Výjimka pro případy kdy není nalezen požadovaný resource
 * Např. transakce s daným ID neexistuje, záznam v databázi nebyl nalezen
 */
public class ResourceNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";

    /**
     * Konstruktor s typem resourceu a ID
     * 
     * @param resourceType typ resourceu (např. "Transakce", "Uživatel")
     * @param resourceId   ID resourceu
     */
    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(
                String.format("%s s ID '%s' nebylo nalezeno", resourceType, resourceId),
                String.format("Resource type: %s, ID: %s", resourceType, resourceId),
                HttpStatus.NOT_FOUND,
                ERROR_CODE,
                resourceId);
    }

    /**
     * Konstruktor s vlastní zprávou
     * 
     * @param message vlastní chybová zpráva
     */
    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
