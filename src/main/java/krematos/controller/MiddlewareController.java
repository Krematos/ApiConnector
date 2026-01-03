package krematos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import krematos.model.InternalRequest;
import krematos.model.InternalResponse;
import krematos.service.TransactionService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Tag(name = "Middleware API", description = "API pro zpracování transakcí prostřednictvím middleware")
public class MiddlewareController {
    private final TransactionService transactionService;


    // Používá Mono/Flux (reaktivní) pro neblokující chování
    @Operation(summary = "Zpracovat novou transakci", description = "Přijme interní požadavek, transformuje ho a odešle na externí systém.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transakce úspěšně zpracována",
                    content = @Content(schema = @Schema(implementation = InternalResponse.class))),
            @ApiResponse(responseCode = "400", description = "Neplatná data (validace selhala)",
                    content = @Content),
            @ApiResponse(responseCode = "503", description = "Externí systém je nedostupný (Fallback aktivní)",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Neplatný API Key",
                    content = @Content)
    })
    @PostMapping("/transaction")
    public Mono<ResponseEntity<InternalResponse>> handleTransaction(@RequestBody InternalRequest request) {
        log.info("Přijat požadavek na zpracování transakce: {}", request);
        // Zavolání servisní vrstvy
        return transactionService.process(request)
                .map(internalResponse -> ResponseEntity.ok(internalResponse)
                );

    }
}
