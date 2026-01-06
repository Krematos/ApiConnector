package krematos.connector;

import lombok.Getter;


@Getter
public class ExternalApiException extends RuntimeException{
    private final String referendeId;
    public ExternalApiException(String message, String referendeId) {
        super(message);
        this.referendeId = referendeId;
    }

}
