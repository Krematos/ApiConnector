package krematos.controller;

import krematos.model.InternalRequest;
import krematos.model.InternalResponse;
import krematos.service.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("api/middleware/v1")
public class MiddlewareController {
    private final TransactionService transactionService;

    public MiddlewareController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // Používá Mono/Flux (reaktivní) pro neblokující chování
    @PostMapping("/transaction")
    public Mono<ResponseEntity<InternalResponse>> handleTransaction(@RequestBody InternalRequest request) {

        // Zavolání servisní vrstvy
        return transactionService.process(request)
                .map(ResponseEntity::ok) // Úspěšná odpověď 200 OK
                .onErrorResume(RuntimeException.class, e -> {
                    // Zde zachytí chyby po selhání všech opakování
                    log.error("Chyba při zpracování transakce: {}", e.getMessage());
                    // Návrat 503 Service Unavailable nebo 500 Internal Server Error
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST) // HTTP 400
                            .body(new InternalResponse(false, e.getMessage(), request.getInternalOrderId())));
                })
                .onErrorResume(RuntimeException.class, e -> {
                    log.error("Kritická chyba (5xx/Timeout): {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE) // HTTP 503
                            .body(new InternalResponse(false, "Služba externího API je dočasně nedostupná.", request.getInternalOrderId())));
                })
                ;
    }
}
