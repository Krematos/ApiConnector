package krematos.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Konfigurace OpenAPI (Swagger UI).
 *
 */
@Configuration
public class OpenApiConfig {
        // Konstanty pro klíče zabezpečení
        private static final String SECURITY_SCHEME_NAME = "ApiKeyAuth";
        private static final String API_KEY_HEADER = "X-API-KEY";

        @Bean
        public OpenAPI customOpenAPI(
                @Value("${application.description:Integrační vrstva pro transformaci transakcí}") String appDescription,
                @Value("${application.version:1.0.0}") String appVersion,
                @Value("${application.server.url:http://localhost:8080}") String serverUrl) {

                return new OpenAPI()
                        // 1. INFO SEKCE
                        .info(new Info()
                                .title("Krematos Integration Middleware")
                                .version(appVersion) // Dynamická verze (např. z CI/CD pipeline)
                                .description(appDescription)
                                .contact(new Contact()
                                        .name("KrematosDEV")
                                        .email("dev@krematos.cz")
                                        .url("https://wiki.krematos.internal/middleware")) // Odkaz na interní wiki
                                .license(new License()
                                        .name("Education") // Důležité pro audit licencí
                                        .url("https://krematos.cz/licenses")))

                        // 2. SERVER SEKCE (Umožňuje přepínat prostředí v Swagger UI)
                        .servers(List.of(
                                new Server().url(serverUrl).description("Aktuální prostředí"),
                                new Server().url("https://api-dev.krematos.cz").description("Vývojové prostředí (DEV)"),
                                new Server().url("https://api.krematos.cz").description("Produkční prostředí (PROD)")
                        ))

                        // 3. SECURITY SEKCE
                        // Přidá požadavek na bezpečnost globálně
                        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                        // Definuje komponentu zabezpečení (API Key)
                        .components(new Components()
                                .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                        new SecurityScheme()
                                                .name(SECURITY_SCHEME_NAME)
                                                .type(SecurityScheme.Type.APIKEY)
                                                .in(SecurityScheme.In.HEADER)
                                                .name(API_KEY_HEADER)
                                                .description("Zadejte API klíč pro přístup k endpointům.")
                        ));
        }
}
