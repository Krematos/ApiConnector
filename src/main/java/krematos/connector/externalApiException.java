package krematos.connector;

public class externalApiException extends RuntimeException{
    private final String referendeId;
    public externalApiException(String message, String referendeId) {
        super(message);
        this.referendeId = referendeId;
    }

    public String getReferendeId() {
        return referendeId;
    }

}
