package krematos.consumer;


import krematos.model.ExternalApiRequest;
import krematos.model.InternalRequest;
import krematos.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailedTransactionConsumer {

    private final TransactionService transactionService;

    // Framework automaticky deserializuje JSON na ExternalApiRequest
    @Bean
    public Consumer<ExternalApiRequest> processFailedTransaction() {
        return externalApiRequest -> {
            log.info("Přijata zpráva: {}", externalApiRequest);

            InternalRequest internalRequest = mapToInternal(externalApiRequest);

            // Blokující volání pro zachování transakčnosti (nebo .block() pokud je service reaktivní)
            // Spring Cloud Stream se postará o ACK/NACK automaticky podle toho, zda vyletí výjimka.
            transactionService.process(internalRequest)
                    .doOnSuccess(s -> log.info("RETRY ÚSPĚŠNÉ"))
                    .doOnError(e -> log.error("RETRY SELHALO"))
                    .block(); // V rámci Streamu je často lepší počkat na výsledek
        };
    }

    private InternalRequest mapToInternal(ExternalApiRequest request) {
        return new InternalRequest(
                request.getTransactionId(),
                request.getAmount(),
                request.getCurrency(),
                "RETRY_SERVICE",
                LocalDateTime.now()
        );
    }



}
