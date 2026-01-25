package krematos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * Manager, který se stará o získávání a refreshování tokenů (OAuth2).
     */
    @Bean
    public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(ReactiveClientRegistrationRepository clientRegistrationRepository) {

        // Konfigurace providera pro Client Credentials flow
        ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider =
                ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials() // povoluje flow pro komunikaci server-server
                        .build();

        // Vytvoření managera
        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrationRepository));

        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    /**
     * Vytvoří WebClienta, který je již nakonfigurovaný:
     * 1. Má Base URL
     * 2. Má výchozí hlavičky
     * 3. Automaticky přidává OAuth2 Bearer token
     */
    @Bean
    public WebClient externalSystemWebClient(WebClient.Builder builder,
                                             ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
                                             @Value("${external.api.base-url}") String baseUrl) {

        // Vytvoření filtru pro OAuth2
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        // Nastaví výchozí registraci z application.yml
        oauth2Client.setDefaultClientRegistrationId("external-api-client");

        return builder
                .filter(oauth2Client) // Aplikace OAuth2 filtru
                .baseUrl(baseUrl)     // Nastavení URL z configu
                .defaultHeaders(headers -> {
                    headers.add("Content-Type", "application/json");
                    headers.add("Accept", "application/json");
                    headers.add("User-Agent", "Krematos-Middleware-Connector/1.0");
                })
                .build();
    }
}
