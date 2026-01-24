package krematos.controller;

import krematos.dto.ApiError;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
 * - Používá strukturovaný ApiError pro konzistentní API
 * - Všechny handlery jsou reaktivní (vrací Mono)
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
         * Vyčistí MDC kontext
         * Mělo by být voláno na konci každého exception handleru
         */
        private void clearMDC() {
                MDC.clear();
        }

        /**
         * Ošetření business výjimek (všechny naše custom výjimky)
         * Tyto výjimky obsahují všechny potřebné informace pro vytvoření odpovědi
         */
        @ExceptionHandler(BusinessException.class)
        public Mono<ResponseEntity<ApiError>> handleBusinessException(
                        BusinessException ex,
                        ServerWebExchange exchange) {

                String traceId = generateTraceId();

                try {
                        // Loguje podle závažnosti - business výjimky jsou očekávané, takže WARN
                        log.warn("Business výjimka: {} | TraceID: {} | ReferenceID: {} | ErrorCode: {}",
                                        ex.getMessage(), traceId, ex.getReferenceId(), ex.getErrorCode());

                        ApiError apiError = ApiError.builder()
                                        .timestamp(Instant.now())
                                        .status(ex.getHttpStatus().value())
                                        .error(ex.getHttpStatus().getReasonPhrase())
                                        .message(ex.getMessage())
                                        .details(isDevEnvironment() ? ex.getDetailMessage() : null)
                                        .path(getRequestPath(exchange))
                                        .traceId(traceId)
                                        .referenceId(ex.getReferenceId())
                                        .errorCode(ex.getErrorCode())
                                        .build();

                        return Mono.just(ResponseEntity.status(ex.getHttpStatus()).body(apiError));
                } finally {
                        clearMDC();
                }
        }

        /**
         * Ošetření ResourceNotFoundException
         * Vrací 404 Not Found
         */
        @ExceptionHandler(ResourceNotFoundException.class)
        public Mono<ResponseEntity<ApiError>> handleResourceNotFoundException(
                        ResourceNotFoundException ex,
                        ServerWebExchange exchange) {

                String traceId = generateTraceId();

                try {
                        log.warn("Resource not found: {} | TraceID: {}", ex.getMessage(), traceId);

                        ApiError apiError = ApiError.builder()
                                        .timestamp(Instant.now())
                                        .status(HttpStatus.NOT_FOUND.value())
                                        .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                                        .message(ex.getMessage())
                                        .details(isDevEnvironment() ? ex.getDetailMessage() : null)
                                        .path(getRequestPath(exchange))
                                        .traceId(traceId)
                                        .errorCode(ex.getErrorCode())
                                        .build();

                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiError));
                } finally {
                        clearMDC();
                }
        }

        /**
         * Ošetření ValidationException
         * Vrací 400 Bad Request s detaily validačních chyb
         */
        @ExceptionHandler(ValidationException.class)
        public Mono<ResponseEntity<ApiError>> handleValidationException(
                        ValidationException ex,
                        ServerWebExchange exchange) {

                String traceId = generateTraceId();

                try {
                        log.warn("Validation exception: {} | TraceID: {}", ex.getMessage(), traceId);

                        ApiError apiError = ApiError.builder()
                                        .timestamp(Instant.now())
                                        .status(HttpStatus.BAD_REQUEST.value())
                                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                                        .message(ex.getMessage())
                                        .details(isDevEnvironment() ? ex.getDetailMessage() : null)
                                        .path(getRequestPath(exchange))
                                        .traceId(traceId)
                                        .errorCode(ex.getErrorCode())
                                        .build();

                        return Mono.just(ResponseEntity.badRequest().body(apiError));
                } finally {
                        clearMDC();
                }
        }

        /**
         * Ošetření ExternalServiceException
         * Vrací 502 Bad Gateway nebo 503 Service Unavailable
         */
        @ExceptionHandler(ExternalServiceException.class)
        public Mono<ResponseEntity<ApiError>> handleExternalServiceException(
                        ExternalServiceException ex,
                        ServerWebExchange exchange) {

                String traceId = generateTraceId();

                try {
                        log.error("External service error: {} | TraceID: {} | ReferenceID: {}",
                                        ex.getMessage(), traceId, ex.getReferenceId(), ex);

                        ApiError apiError = ApiError.builder()
                                        .timestamp(Instant.now())
                                        .status(ex.getHttpStatus().value())
                                        .error(ex.getHttpStatus().getReasonPhrase())
                                        .message(ex.getMessage())
                                        .details(isDevEnvironment() ? ex.getDetailMessage() : null)
                                        .path(getRequestPath(exchange))
                                        .traceId(traceId)
                                        .referenceId(ex.getReferenceId())
                                        .errorCode(ex.getErrorCode())
                                        .build();

                        return Mono.just(ResponseEntity.status(ex.getHttpStatus()).body(apiError));
                } finally {
                        clearMDC();
                }
        }

        /**
         * Ošetření RateLimitException
         * Vrací 429 Too Many Requests
         */
        @ExceptionHandler(RateLimitException.class)
        public Mono<ResponseEntity<ApiError>> handleRateLimitException(
                        RateLimitException ex,
                        ServerWebExchange exchange) {

                String traceId = generateTraceId();

                try {
                        log.warn("Rate limit exceeded: {} | TraceID: {}", ex.getMessage(), traceId);

                        ApiError apiError = ApiError.builder()
                                        .timestamp(Instant.now())
                                        .status(HttpStatus.TOO_MANY_REQUESTS.value())
                                        .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                                        .message(ex.getMessage())
                                        .details(isDevEnvironment() ? ex.getDetailMessage() : null)
                                        .path(getRequestPath(exchange))
                                        .traceId(traceId)
                                        .errorCode(ex.getErrorCode())
                                        .build();

                        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(apiError));
                } finally {
                        clearMDC();
                }
        }

        /**
         * Ošetření validačních chyb ze Spring WebFlux
         * Nastává když @Valid anotace selže na request objektu
         */
        @ExceptionHandler(WebExchangeBindException.class)
        public Mono<ResponseEntity<ApiError>> handleWebExchangeBindException(
                        WebExchangeBindException ex,
                        ServerWebExchange exchange) {

                String traceId = generateTraceId();

                try {
                        // Vytvoří mapu field -> error message pro lepší strukturu
                        Map<String, String> fieldErrors = new HashMap<>();
                        ex.getBindingResult().getAllErrors().forEach(error -> {
                                if (error instanceof FieldError) {
                                        FieldError fieldError = (FieldError) error;
                                        fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
                                } else {
                                        fieldErrors.put("global", error.getDefaultMessage());
                                }
                        });

                        // Spojí všechny validační chyby do jedné zprávy
                        String validationErrors = ex.getBindingResult().getAllErrors().stream()
                                        .map(error -> {
                                                if (error instanceof FieldError) {
                                                        FieldError fieldError = (FieldError) error;
                                                        return String.format("%s: %s", fieldError.getField(),
                                                                        fieldError.getDefaultMessage());
                                                }
                                                return error.getDefaultMessage();
                                        })
                                        .collect(Collectors.joining(", "));

                        log.warn("Validační chyba: {} | TraceID: {}", validationErrors, traceId);

                        ApiError apiError = ApiError.builder()
                                        .timestamp(Instant.now())
                                        .status(HttpStatus.BAD_REQUEST.value())
                                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                                        .message("Validace vstupních dat selhala")
                                        .details(isDevEnvironment() ? validationErrors : null)
                                        .path(getRequestPath(exchange))
                                        .traceId(traceId)
                                        .errorCode("VALIDATION_ERROR")
                                        .build();

                        return Mono.just(ResponseEntity.badRequest().body(apiError));
                } finally {
                        clearMDC();
                }
        }

        /**
         * Ošetření ResponseStatusException - výjimky se specifickým HTTP statusem
         * Používá Spring framework pro indikaci různých HTTP stavů
         */
        @ExceptionHandler(ResponseStatusException.class)
        public Mono<ResponseEntity<ApiError>> handleResponseStatusException(
                        ResponseStatusException ex,
                        ServerWebExchange exchange) {

                String traceId = generateTraceId();

                try {
                        log.warn("ResponseStatus výjimka: {} | Status: {} | TraceID: {}",
                                        ex.getReason(), ex.getStatusCode(), traceId);

                        ApiError apiError = ApiError.builder()
                                        .timestamp(Instant.now())
                                        .status(ex.getStatusCode().value())
                                        .error(ex.getStatusCode().toString())
                                        .message(ex.getReason() != null ? ex.getReason() : "Požadavek selhal")
                                        .details(isDevEnvironment() ? ex.getMessage() : null)
                                        .path(getRequestPath(exchange))
                                        .traceId(traceId)
                                        .build();

                        return Mono.just(ResponseEntity.status(ex.getStatusCode()).body(apiError));
                } finally {
                        clearMDC();
                }
        }

        /**
         * Ošetření IllegalArgumentException - neplatné argumenty metod
         * Obvykle indikuje programátorskou chybu nebo špatná vstupní data
         */
        @ExceptionHandler(IllegalArgumentException.class)
        public Mono<ResponseEntity<ApiError>> handleIllegalArgumentException(
                        IllegalArgumentException ex,
                        ServerWebExchange exchange) {

                String traceId = generateTraceId();

                try {
                        log.warn("Neplatný argument: {} | TraceID: {}", ex.getMessage(), traceId);

                        ApiError apiError = ApiError.builder()
                                        .timestamp(Instant.now())
                                        .status(HttpStatus.BAD_REQUEST.value())
                                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                                        .message("Neplatná vstupní data")
                                        .details(isDevEnvironment() ? ex.getMessage() : null)
                                        .path(getRequestPath(exchange))
                                        .traceId(traceId)
                                        .errorCode("INVALID_ARGUMENT")
                                        .build();

                        return Mono.just(ResponseEntity.badRequest().body(apiError));
                } finally {
                        clearMDC();
                }
        }

        /**
         * Catch-all handler pro všechny neočekávané výjimky
         * Loguje plný stack trace a vrací generickou chybu v produkci
         */
        @ExceptionHandler(Exception.class)
        public Mono<ResponseEntity<ApiError>> handleGenericException(
                        Exception ex,
                        ServerWebExchange exchange) {

                String traceId = generateTraceId();

                try {
                        // Loguje plný stack trace pro možnost debugování
                        log.error("Neočekávaná výjimka zachycena | TraceID: {} | Exception: {}",
                                        traceId, ex.getClass().getName(), ex);

                        // V produkci zobrazuje pouze generické zprávy
                        String userMessage = isDevEnvironment()
                                        ? String.format("Interní chyba: %s", ex.getMessage())
                                        : "Služba je dočasně nedostupná. Kontaktujte podporu s TraceID: " + traceId;

                        ApiError apiError = ApiError.builder()
                                        .timestamp(Instant.now())
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                        .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                                        .message(userMessage)
                                        .details(isDevEnvironment() ? ex.toString() : null)
                                        .path(getRequestPath(exchange))
                                        .traceId(traceId)
                                        .errorCode("INTERNAL_ERROR")
                                        .build();

                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiError));
                } finally {
                        clearMDC();
                }
        }
}
