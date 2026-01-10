package krematos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import krematos.model.InternalRequest;
import krematos.model.InternalResponse;
import krematos.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @PostMapping
    @Operation(
            summary = "Vytvořit novou transakci",
            description = "Přijme požadavek na transakci, validuje jej a odešle ke zpracování. Vyžaduje API klíč.",
            security = @SecurityRequirement(name = "ApiKeyAuth") // Tímto se v Swaggeru objeví ikona zámečku
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transakce úspěšně přijata"),
            @ApiResponse(responseCode = "400", description = "Neplatná vstupní data (chybí měna nebo částka)"),
            @ApiResponse(responseCode = "401", description = "Neplatný nebo chybějící API klíč")
    })
    public Mono<InternalResponse> createTransaction(@RequestBody InternalRequest request) {
        return transactionService.process(request);
    }

}
