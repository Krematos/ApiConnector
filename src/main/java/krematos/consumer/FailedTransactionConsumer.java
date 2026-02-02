package krematos.consumer;


import krematos.model.ExternalApiRequest;
import krematos.model.InternalRequest;
import krematos.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailedTransactionConsumer {

    private final TransactionService transactionService;


    //  Function<Flux, Mono<Void>> pro plně reaktivní stream
    @Bean
    public Function<Flux<ExternalApiRequest>, Mono<Void>> processFailedTransaction() {
        return flux -> flux
                .flatMap(this::processSingleRequest) // flatMap zpracovává paralelně
                .then(); // Po zpracování celého streamu (nebo při běhu) vrací signál dokončení
    }

    private Mono<Void> processSingleRequest(ExternalApiRequest externalApiRequest) {
        log.info("RETRY CONSUMER: Přijata zpráva k opakování: {}", externalApiRequest.getTransactionId());

        InternalRequest internalRequest = mapToInternal(externalApiRequest);

        // Volá service.process (který vrací Mono)
        return transactionService.process(internalRequest)
                .doOnSuccess(s -> log.info("RETRY ÚSPĚŠNÉ pro ID: {}", externalApiRequest.getTransactionId()))
                .doOnError(e -> log.error("RETRY SELHALO pro ID: {}. Chyba: {}", externalApiRequest.getTransactionId(), e.getMessage()))
                .onErrorResume(e -> Mono.empty()) // Chybu "spolkne", aby neshodila celý stream 
                .then();
    }

    private InternalRequest mapToInternal(ExternalApiRequest request) {
        return new InternalRequest(
                request.getTransactionId(),
                request.getAmount(),
                request.getCurrency(),
                "RETRY_SERVICE",
                Instant.now()
        );
    }



}
