package krematos.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Krematos Integration Middleware",
                version = "1.0",
                description = "Integrační vrstva pro transformaci a přeposílání transakcí.",
                contact = @Contact(name = "Tým Integrace", email = "dev@krematos.cz")
        ),
        // Aplikuje zabezpečení globálně na všechny endpointy
        security = @SecurityRequirement(name = "ApiKeyAuth")
)
@SecurityScheme(
        name = "ApiKeyAuth",        // Název schématu (použijeme v @SecurityRequirement)
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-API-KEY"     // Název hlavičky, kterou Swagger pošle
)
public class OpenApiConfig {

}
