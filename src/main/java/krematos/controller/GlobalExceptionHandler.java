package krematos.controller;

import krematos.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Globální ošetření výjimek pro všechny kontrolery
 * Poskytuje jednotné zpracování chyb napříč celou aplikací
 * 
 * Best practices:
 * - Všechny chyby loguje s trace ID pro snadné vyhledání v logových souborech
 * - V dev/local prostředí vrací detailní informace pro debugging
 * - V produkčním prostředí skrývá citlivé detaily a vrací generické zprávy
 * - Používá strukturovaný ErrorResponse pro konzistentní API
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    /**
     * Kontrola zda běžíme v dev/local prostředí
     * V těchto prostředích zobrazujeme detailní informace pro debugging
     */
    private boolean isDevEnvironment() {
        return "dev".equals(activeProfile) || "local".equals(activeProfile);
    }

    /**
     * Vygeneruje unikátní trace ID a přidá ho do MDC kontextu
     * Trace ID se poté objeví ve všech log zprávách
     * 
     * @return vygenerované trace ID
     */
    private String generateTraceId() {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        return traceId;
    }

    /**
     * Získá cestu z request pro zahrnutí v error response
     */
    private String getRequestPath(ServerWebExchange exchange) {
        return exchange != null ? exchange.getRequest().getPath().value() : "unknown";
    }

    /**
     * Ošetření business výjimek (všechny naše custom výjimky)
     * Tyto výjimky obsahují všechny potřebné informace pro vytvoření odpovědi
     */
    @ExceptionHandler(BusinessException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBusinessException(
            BusinessException ex,
            ServerWebExchange exchange) {

        String traceId = generateTraceId();

        // Logujeme podle závažnosti - business výjimky jsou očekávané, takže WARN
        log.warn("Business výjimka: {} | TraceID: {} | ReferenceID: {} | ErrorCode: {}",
                ex.getMessage(), traceId, ex.getReferenceId(), ex.getErrorCode());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getHttpStatus().value())
                .error(ex.getHttpStatus().getReasonPhrase())
                .message(ex.getMessage())
                .details(isDevEnvironment() ? ex.getDetailMessage() : null)
                .path(getRequestPath(exchange))
                .traceId(traceId)
                .referenceId(ex.getReferenceId())
                .errorCode(ex.getErrorCode())
                .build();

        MDC.clear();
        return Mono.just(ResponseEntity.status(ex.getHttpStatus()).body(errorResponse));
    }

    /**
     * Ošetření validačních chyb ze Spring WebFlux
     * Nastává když @Valid anotace selže na request objektu
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationException(
            WebExchangeBindException ex,
            ServerWebExchange exchange) {

        String traceId = generateTraceId();

        // Spojíme všechny validační chyby do jedné zprávy
        String validationErrors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    if (error instanceof FieldError) {
                        FieldError fieldError = (FieldError) error;
                        return String.format("%s: %s", fieldError.getField(), fieldError.getDefaultMessage());
                    }
                    return error.getDefaultMessage();
                })
                .collect(Collectors.joining(", "));

        log.warn("Validační chyba: {} | TraceID: {}", validationErrors, traceId);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validace vstupních dat selhala")
                .details(isDevEnvironment() ? validationErrors : null)
                .path(getRequestPath(exchange))
                .traceId(traceId)
                .errorCode("VALIDATION_ERROR")
                .build();

        MDC.clear();
        return Mono.just(ResponseEntity.badRequest().body(errorResponse));
    }

    /**
     * Ošetření ResponseStatusException - výjimky se specifickým HTTP statusem
     * Používá Spring framework pro indikaci různých HTTP stavů
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleResponseStatusException(
            ResponseStatusException ex,
            ServerWebExchange exchange) {

        String traceId = generateTraceId();

        log.warn("ResponseStatus výjimka: {} | Status: {} | TraceID: {}",
                ex.getReason(), ex.getStatusCode(), traceId);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatusCode().value())
                .error(ex.getStatusCode().toString())
                .message(ex.getReason() != null ? ex.getReason() : "Požadavek selhal")
                .details(isDevEnvironment() ? ex.getMessage() : null)
                .path(getRequestPath(exchange))
                .traceId(traceId)
                .build();

        MDC.clear();
        return Mono.just(ResponseEntity.status(ex.getStatusCode()).body(errorResponse));
    }

    /**
     * Ošetření IllegalArgumentException - neplatné argumenty metod
     * Obvykle indikuje programátorskou chybu nebo špatná vstupní data
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgumentException(
            IllegalArgumentException ex,
            ServerWebExchange exchange) {

        String traceId = generateTraceId();

        log.warn("Neplatný argument: {} | TraceID: {}", ex.getMessage(), traceId);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Neplatná vstupní data")
                .details(isDevEnvironment() ? ex.getMessage() : null)
                .path(getRequestPath(exchange))
                .traceId(traceId)
                .errorCode("INVALID_ARGUMENT")
                .build();

        MDC.clear();
        return Mono.just(ResponseEntity.badRequest().body(errorResponse));
    }

    /**
     * Catch-all handler pro všechny neočekávané výjimky
     * Toto je poslední obranná linie - loguje ERROR protože jde o neočekávaný stav
     * 
     * DŮLEŽITÉ: V produkci NIKDY neodhalujeme detaily neočekávaných chyb
     * aby nedošlo k úniku citlivých informací (např. cesty v souborovém systému,
     * SQL queries)
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(
            Exception ex,
            ServerWebExchange exchange) {

        String traceId = generateTraceId();

        // ERROR level protože jde o neočekávanou výjimku
        // Logujeme plný stack trace pro možnost debugování
        log.error("Neočekávaná výjimka zachycena | TraceID: {} | Exception: {}",
                traceId, ex.getClass().getName(), ex);

        // V produkci zobrazujeme pouze generické zprávy
        String userMessage = isDevEnvironment()
                ? String.format("Interní chyba: %s", ex.getMessage())
                : "Služba je dočasně nedostupná. Kontaktujte podporu s TraceID: " + traceId;

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message(userMessage)
                .details(isDevEnvironment() ? ex.toString() : null)
                .path(getRequestPath(exchange))
                .traceId(traceId)
                .errorCode("INTERNAL_ERROR")
                .build();

        MDC.clear();
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
    }
}
