package krematos.service;

import krematos.connector.ExternalSystemConnector;
import krematos.model.ExternalApiRequest;
import krematos.model.ExternalApiResponse;
import krematos.model.InternalRequest;
import krematos.model.InternalResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;

@Service
public class TransactionService {
    private final ExternalSystemConnector externalSystemConnector;

    public TransactionService(ExternalSystemConnector externalSystemConnector) {
        this.externalSystemConnector = externalSystemConnector;
    }

    // Metoda pro transformaci a orchestraci
    public Mono<InternalResponse> process(InternalRequest internalRequest) {
        // 1. Transformace interního požadavku na externí formát
        ExternalApiRequest externalRequest = transformToExternal(internalRequest);

        // 2. Volání externího API
        Mono<ExternalApiResponse> apiResponseMono = externalSystemConnector.sendRequest(externalRequest);

        // 3. Transformace externí odpovědi zpět na interní formát a návrat
        return apiResponseMono
                .map(externalResponse -> transformToInternal(externalResponse, internalRequest.getInternalOrderId()))
                .doOnError(error -> System.err.println("Kompletní transakce selhala po všech opakováních: " + error.getMessage()));
    }

    // --- Transformace ---
    private ExternalApiRequest transformToExternal(InternalRequest internal) {
        // Příklad transformace: Složitější mapování a konverze hodnot
        String protocol = mapServiceTypeToProtocol(internal.getServiceType());

        return new ExternalApiRequest(
                // Vytvoří unikátní ID pro externí systém
                internal.getInternalOrderId() + "-" + internal.getRequestedAt().format(DateTimeFormatter.ofPattern("yyMMddHHmm")),
                internal.getAmount().doubleValue(), // Převede BigDecimal na double
                protocol
        );
    }

    private InternalResponse transformToInternal(ExternalApiResponse external, String internalOrderId) {
        boolean success = external.getStatusCode() == 200 && "COMPLETED".equals(external.getDetailStatus());
        String message;

        if (success) {
            message = "Transakce úspěšně dokončena. ID: " + external.getConfirmationId();
        } else {
            message = String.format("Transakce selhala. Status: %d, Detail: %s",
                    external.getStatusCode(), external.getDetailStatus());
        }

        return new InternalResponse(
                success,
                message,
                internalOrderId
        );
    }

    private String mapServiceTypeToProtocol(String serviceType) {
        switch (serviceType.toUpperCase()) {
            case "PREMIUM":
                return "PROTOCOL_X";
            case "STANDARD":
                return "PROTOCOL_Y";
            default:
                return "PROTOCOL_DEFAULT";
        }
    }
}
