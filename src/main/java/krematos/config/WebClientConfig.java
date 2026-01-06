package krematos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public ReactiveOAuth2AuthorizedClientManager auth2AuthorizedClientManager(ReactiveClientRegistrationRepository clientRegistrationRepository) {

        // Vytvoření providera, který podporuje client credentials flow
        ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider =
                ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build();

        // Vytvoření a konfigurace managera, který drží autorizované klienty
        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrationRepository));

        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;

    }

    @Bean
    public WebClient externalSystemWebClient(WebClient.Builder builder,
                                             ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {

        // Vytvoření filtru pro OAuth2
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        // Zde nastaví výchozí registraci (aby to nebylo nutné psát u každého requestu)
        // Název musí odpovídat tomu v application.yml (external-system-client)
        oauth2Client.setDefaultClientRegistrationId("external-system-client");

        return builder
                .filter(oauth2Client) // Přidání filtru do WebClienta
                .build();
    }
}
