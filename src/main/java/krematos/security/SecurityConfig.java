package krematos.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    // Konstanty pro lepší čitelnost a prevenci překlepů
    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String ROLE_API_USER = "ROLE_API_USER";

    @Value("${security.api-key}")
    private String apiKey;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            ReactiveAuthenticationManager authManager,
                                                            ServerAuthenticationConverter authConverter) {
        // Konfigurace filtru. Ten spojuje Converter (získání dat) a Manager (ověření dat)
        AuthenticationWebFilter apiKeyFilter = new AuthenticationWebFilter(authManager);
        apiKeyFilter.setServerAuthenticationConverter(authConverter);
        // Volitelné: Nastavení handleru pro případ selhání autentizace (např. vrátit JSON místo HTML erroru)
        // apiKeyFilter.setAuthenticationFailureHandler(...);
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // Pro API vypíná CSRF
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable) // Logout u API klíčů nedává smysl
                .authorizeExchange(exchanges -> exchanges
                        // Veřejné endpointy (dokumentace, monitoring, atd.)
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll() // Swagger přístupné všem
                        .pathMatchers("/webjars/**").permitAll() // Webjars přístupné všem
                        .pathMatchers("/actuator/health/**", "/actuator/prometheus").permitAll() // Health veřejný
                        .pathMatchers("/actuator/**").hasAuthority(ROLE_API_USER) // Ostatní metriky raději zabezpečit
                        .pathMatchers("/mock-external/**").permitAll() // Mock endpointy veřejné (pouze pro testování)

                        // Aplikační endpointy
                        .pathMatchers("/api/**").authenticated() // Vše pod /api musí být auth
                        // Vše ostatní zakázat (Best Practice: Deny by default)
                        .anyExchange().denyAll()
                )
                // Přidá filtr před standardní autentizaci
                .addFilterAt(apiKeyFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
    /**
     * CONVERTER: Pouze extrahuje data z requestu. Neověřuje je.
     * Best Practice: Oddělit získání tokenu od jeho validace.
     */
    @Bean
    public ServerAuthenticationConverter apiKeyConverter() {
        return exchange -> {
            String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
            // Pokud klíč chybí, vrátí Mono.empty().
            // Tím říká Springu: "Tento request neobsahuje autentizační údaje pro tento filtr."
            // Spring pak buď zkusí jiný filtr, nebo vrátí 401 (protože endpoint vyžaduje auth).
            if(!StringUtils.hasText(apiKey)) {
                return Mono.empty();
            }
            // Vytvoří dočasný token s klíčem jako "credentials"
            return Mono.just(new UsernamePasswordAuthenticationToken(null, apiKey));
        };
    }
    /**
     * MANAGER: Ověřuje platnost tokenu.
     * Best Practice: Validace probíhá zde, nikoliv v Converteru.
     */
    @Bean
    public ReactiveAuthenticationManager apiKeyAuthManager() {
        return authentication -> {
            // Získá "heslo" (API klíč) z credentials
            String presentedKey = (String) authentication.getCredentials();

            // Bezpečnostní porovnání (v reálné produkci by zde bylo volání do DB nebo Hash check)
            if (apiKey.equals(presentedKey)) {

                // ÚSPĚCH: Vytvoří nový, plně autentizovaný token.
                // 1. Principal: Nastaví bezpečnou identitu (např. název služby). To se objeví v logu.
                // 2. Credentials: null (Vymazání tajemství z paměti - Security Best Practice).
                // 3. Authorities: Seznam oprávnění.
                Authentication auth = new UsernamePasswordAuthenticationToken(
                        "API_CLIENT_SERVICE", // Toto jméno se zobrazí v logu místo tajného klíče
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority(ROLE_API_USER))
                );
                return Mono.just(auth);
            } else {
                // CHYBA: Vyhodí výjimku, která vyústí v 401 Unauthorized
                return Mono.error(new BadCredentialsException("Invalid API Key"));
            }
        };
    }
}

