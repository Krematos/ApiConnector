package krematos.controller;

import krematos.dto.ApiError;
import krematos.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Komplexní testy pro GlobalExceptionHandler
 * Testuje všechny exception handlery a edge cases
 * 
 * Best practices:
 * - Použití @Nested pro logické seskupení testů
 * - Použití @DisplayName pro čitelné názvy testů
 * - Testování dev vs production chování
 * - Ověření všech polí v error response
 * - Použití StepVerifier pro reaktivní testy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    private ServerWebExchange mockExchange;

    @BeforeEach
    void setUp() {
        mockExchange = mock(ServerWebExchange.class);
        var mockRequest = mock(org.springframework.http.server.reactive.ServerHttpRequest.class);
        var mockPath = mock(org.springframework.http.server.RequestPath.class);

        when(mockExchange.getRequest()).thenReturn(mockRequest);
        when(mockRequest.getPath()).thenReturn(mockPath);
        when(mockPath.value()).thenReturn("/api/test");
    }

    @Nested
    @DisplayName("BusinessException Handling")
    class BusinessExceptionTests {

        @Test
        @DisplayName("Should handle BusinessException with all fields populated")
        void handleBusinessException_WithAllFields() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");

            BusinessException exception = new ResourceNotFoundException(
                    "Resource not found",
                    "Detailed info about missing resource",
                    "REF-123");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleBusinessException(exception, mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.status()).isEqualTo(404);
                        assertThat(error.error()).isEqualTo("Not Found");
                        assertThat(error.message()).isEqualTo("Resource not found");
                        assertThat(error.details()).isEqualTo("Detailed info about missing resource"); // dev mode
                        assertThat(error.path()).isEqualTo("/api/test");
                        assertThat(error.traceId()).isNotNull();
                        assertThat(error.referenceId()).isEqualTo("REF-123");
                        assertThat(error.errorCode()).isNotNull();
                        assertThat(error.timestamp()).isNotNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should hide details in production environment")
        void handleBusinessException_ProductionMode() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "prod");

            BusinessException exception = new ResourceNotFoundException(
                    "Resource not found",
                    "Sensitive internal details",
                    "REF-456");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleBusinessException(exception, mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.details()).isNull(); // Production mode hides details
                        assertThat(error.message()).isEqualTo("Resource not found");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle BusinessException without optional fields")
        void handleBusinessException_MinimalFields() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");

            BusinessException exception = new ValidationException("Invalid input");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleBusinessException(exception, mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.message()).isEqualTo("Invalid input");
                        assertThat(error.referenceId()).isNull();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("ResourceNotFoundException Handling")
    class ResourceNotFoundExceptionTests {

        @Test
        @DisplayName("Should return 404 status for ResourceNotFoundException")
        void handleResourceNotFoundException_Returns404() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");

            ResourceNotFoundException exception = new ResourceNotFoundException(
                    "User with ID 123 not found",
                    "Database query returned no results",
                    "USER-123");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleResourceNotFoundException(exception,
                    mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.status()).isEqualTo(404);
                        assertThat(error.message()).contains("not found");
                        assertThat(error.traceId()).isNotNull();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("ValidationException Handling")
    class ValidationExceptionTests {

        @Test
        @DisplayName("Should return 400 status for ValidationException")
        void handleValidationException_Returns400() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");

            ValidationException exception = new ValidationException(
                    "Invalid email format",
                    "Email must match pattern: ^[A-Za-z0-9+_.-]+@(.+)$");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleValidationException(exception, mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.status()).isEqualTo(400);
                        assertThat(error.message()).isEqualTo("Invalid email format");
                        assertThat(error.details()).contains("pattern"); // dev mode
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("ExternalServiceException Handling")
    class ExternalServiceExceptionTests {

        @Test
        @DisplayName("Should handle external service errors with proper status")
        void handleExternalServiceException_Returns502() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");

            ExternalServiceException exception = new ExternalServiceException(
                    "External API unavailable",
                    "Connection timeout after 30s",
                    "TXN-789");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleExternalServiceException(exception,
                    mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);

                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.status()).isEqualTo(502);
                        assertThat(error.message()).contains("unavailable");
                        assertThat(error.referenceId()).isEqualTo("TXN-789");
                        assertThat(error.details()).contains("timeout"); // dev mode
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("RateLimitException Handling")
    class RateLimitExceptionTests {

        @Test
        @DisplayName("Should return 429 status for RateLimitException")
        void handleRateLimitException_Returns429() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");

            RateLimitException exception = new RateLimitException(
                    "Rate limit exceeded",
                    "Maximum 100 requests per minute allowed");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleRateLimitException(exception, mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.status()).isEqualTo(429);
                        assertThat(error.message()).contains("Rate limit");
                        assertThat(error.details()).contains("100 requests"); // dev mode
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("WebExchangeBindException Handling")
    class WebExchangeBindExceptionTests {

        @Test
        @DisplayName("Should handle validation errors with field details")
        void handleWebExchangeBindException_WithFieldErrors() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");

            Object target = new Object();
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "testObject");
            bindingResult.addError(new FieldError("testObject", "email", "must be a valid email"));
            bindingResult.addError(new FieldError("testObject", "age", "must be greater than 0"));

            WebExchangeBindException exception = new WebExchangeBindException(null, bindingResult);

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleWebExchangeBindException(exception,
                    mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.status()).isEqualTo(400);
                        assertThat(error.message()).isEqualTo("Validace vstupních dat selhala");
                        assertThat(error.errorCode()).isEqualTo("VALIDATION_ERROR");
                        assertThat(error.details()).contains("email");
                        assertThat(error.details()).contains("age");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should hide validation details in production")
        void handleWebExchangeBindException_ProductionMode() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "prod");

            Object target = new Object();
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "testObject");
            bindingResult.addError(new FieldError("testObject", "password", "too weak"));

            WebExchangeBindException exception = new WebExchangeBindException(null, bindingResult);

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleWebExchangeBindException(exception,
                    mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.details()).isNull(); // Production hides details
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("ResponseStatusException Handling")
    class ResponseStatusExceptionTests {

        @Test
        @DisplayName("Should handle ResponseStatusException with custom reason")
        void handleResponseStatusException_WithReason() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");

            ResponseStatusException exception = new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied to this resource");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleResponseStatusException(exception,
                    mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.status()).isEqualTo(403);
                        assertThat(error.message()).isEqualTo("Access denied to this resource");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle ResponseStatusException without reason")
        void handleResponseStatusException_WithoutReason() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");

            ResponseStatusException exception = new ResponseStatusException(HttpStatus.NOT_FOUND);

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleResponseStatusException(exception,
                    mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.message()).isEqualTo("Požadavek selhal");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("IllegalArgumentException Handling")
    class IllegalArgumentExceptionTests {

        @Test
        @DisplayName("Should return 400 for IllegalArgumentException")
        void handleIllegalArgumentException_Returns400() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");

            IllegalArgumentException exception = new IllegalArgumentException(
                    "Invalid parameter: userId cannot be null");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleIllegalArgumentException(exception,
                    mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.status()).isEqualTo(400);
                        assertThat(error.message()).isEqualTo("Neplatná vstupní data");
                        assertThat(error.errorCode()).isEqualTo("INVALID_ARGUMENT");
                        assertThat(error.details()).contains("userId"); // dev mode
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Generic Exception Handling")
    class GenericExceptionTests {

        @Test
        @DisplayName("Should handle unexpected exceptions in dev mode")
        void handleGenericException_DevMode() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");

            Exception exception = new RuntimeException("Unexpected database error");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleGenericException(exception, mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.status()).isEqualTo(500);
                        assertThat(error.message()).contains("Interní chyba");
                        assertThat(error.message()).contains("database error");
                        assertThat(error.errorCode()).isEqualTo("INTERNAL_ERROR");
                        assertThat(error.details()).isNotNull(); // dev mode shows details
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should hide error details in production mode")
        void handleGenericException_ProductionMode() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "prod");

            Exception exception = new RuntimeException("Sensitive internal error");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleGenericException(exception, mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.message()).contains("Služba je dočasně nedostupná");
                        assertThat(error.message()).contains("TraceID");
                        assertThat(error.message()).doesNotContain("Sensitive");
                        assertThat(error.details()).isNull(); // Production hides details
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should include traceId in all error responses")
        void handleGenericException_IncludesTraceId() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "prod");

            Exception exception = new NullPointerException("Null value encountered");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleGenericException(exception, mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.traceId()).isNotNull();
                        assertThat(error.traceId())
                                .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Environment Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null ServerWebExchange gracefully")
        void handleException_NullExchange() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");

            Exception exception = new RuntimeException("Test error");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleGenericException(exception, null);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        ApiError error = response.getBody();
                        assertThat(error).isNotNull();
                        assertThat(error.path()).isEqualTo("unknown");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should recognize local environment as dev")
        void isDevEnvironment_LocalProfile() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "local");

            Exception exception = new RuntimeException("Test");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleGenericException(exception, mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        ApiError error = response.getBody();
                        assertThat(error.details()).isNotNull(); // local is treated as dev
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should treat unknown profiles as production")
        void isDevEnvironment_UnknownProfile() {
            // Given
            ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "staging");

            Exception exception = new RuntimeException("Test");

            // When
            Mono<ResponseEntity<ApiError>> result = exceptionHandler.handleGenericException(exception, mockExchange);

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        ApiError error = response.getBody();
                        assertThat(error.details()).isNull(); // Unknown profiles hide details
                    })
                    .verifyComplete();
        }
    }
}
