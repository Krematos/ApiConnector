package krematos.controller;

import krematos.connector.ExternalApiException;
import krematos.model.InternalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

@Slf4j
@RestControllerAdvice // Globální ošetření výjimek pro všechny kontrolery
public class GlobalExceptionHandler {
    // Zachytí externalApiException a vrátí vhodnou odpověď klientovi
    @ExceptionHandler(ExternalApiException.class)
    public Mono<ResponseEntity<InternalResponse>> handleExternalApiException(ExternalApiException ex) {
        log.warn("Chyba klienta/validace: {}", ex.getMessage());

        InternalResponse response = new InternalResponse(
                false,
                ex.getMessage(), ex.getReferendeId()
                 // Zde není ID requestu, pokud ho výjimka nenese
        );

        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response));
    }
    // Zachytí všechny ostatní neočekávané výjimky
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<InternalResponse>> handleGenericException(Exception ex) {
        log.error("Zachycena neočekávaná výjimka: {}", ex.getMessage());
        InternalResponse response = new InternalResponse(
                false,
                "Služba je dočasně nedostupná.",
                null);
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }
}
