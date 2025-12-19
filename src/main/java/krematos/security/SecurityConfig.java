package krematos.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${security.api-key}")
    private String apiKey;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            ReactiveAuthenticationManager authManager,
                                                            ServerAuthenticationConverter authConverter) {
        // Vytvoříme filtr pro API klíč
        AuthenticationWebFilter apiKeyFilter = new AuthenticationWebFilter(authManager);
        apiKeyFilter.setServerAuthenticationConverter(authConverter);

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // Pro API vypínáme CSRF
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/**").authenticated() // Vše pod /api musí být auth
                        .anyExchange().permitAll()
                )
                // Přidáme náš filtr před standardní autentizaci
                .addFilterAt(apiKeyFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public ServerAuthenticationConverter apiKeyConverter() {
        return exchange -> {
            String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-KEY");
            if (apiKey == null) {
                return Mono.empty();
            }
            // Vytvoří dočasný token s klíčem jako "credentials"
            return Mono.just(new UsernamePasswordAuthenticationToken(apiKey, apiKey));
        };
    }

    @Bean
    public ReactiveAuthenticationManager apiKeyAuthManager() {
        return authentication -> {
            String principal = authentication.getPrincipal().toString();

            if (apiKey.equals(principal)) {
                // Pokud klíč sedí, vrátíme plně autentizovaný token
                return Mono.just(new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_USER"))
                ));
            } else {
                return Mono.error(new org.springframework.security.authentication.BadCredentialsException("Neplatný API klíč"));
            }
        };
    }
}

