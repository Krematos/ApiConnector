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
        log.info("Přijat požadavek na zpracování transakce: {}", request);
        // Zavolání servisní vrstvy
        return transactionService.process(request)
                .map(internalResponse -> ResponseEntity.ok(internalResponse)
                );

    }
}
