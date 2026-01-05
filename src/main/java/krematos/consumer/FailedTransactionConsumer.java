package krematos.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import krematos.model.ExternalApiRequest;
import krematos.model.InternalRequest;
import krematos.model.InternalResponse;
import krematos.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.Receiver;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailedTransactionConsumer implements CommandLineRunner {

    private final Receiver receiver;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    private final String QUEUE_NAME = "failed.transaction.queue";

    @Override
    public void run(String... args){
        log.info("Spouštím spotřebitele pro frontu: {}", QUEUE_NAME);
        receiver.consumeAutoAck(QUEUE_NAME)
            .subscribe(delivery -> {
                try {
                    String json = new String(delivery.getBody());
                    log.info("Přijatá zpráva z fronty {}: {}", QUEUE_NAME, json);

                    // Deserializace JSON na InternalRequest
                    ExternalApiRequest externalApiRequest = objectMapper.readValue(json, ExternalApiRequest.class);

                    // Mapování na InternalRequest
                    InternalRequest internalRequest = new InternalRequest(
                            externalApiRequest.getTransactionId(),
                            BigDecimal.valueOf(externalApiRequest.getAmount()),
                            externalApiRequest.getCurrency(),
                            "RETRY_SERVICE",
                            LocalDateTime.now()
                    );

                    // Zpracování transakce
                    transactionService.process(internalRequest)
                            .subscribe(success -> {log.info("RETRY ÚSPĚŠNÉ!  Transakce {} byla zpracována.", internalRequest.getInternalOrderId());
                            ((AcknowledgableDelivery) delivery).ack();
                                    },
                                    error -> {
                                        log.error("RETRY SELHALO: {}. Zpráva zůstává ve frontě (nebo ji lze poslat do Dead Letter).", error.getMessage());
                                        ((AcknowledgableDelivery) delivery).ack();
                                    });

                } catch (Exception e) {
                    log.error("Chyba při zpracování zprávy z fronty {}: {}", e);
                    ((AcknowledgableDelivery) delivery).ack();
                }
            });
    }
}
