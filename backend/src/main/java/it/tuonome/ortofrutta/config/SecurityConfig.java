package it.tuonome.ortofrutta.config;

import it.tuonome.ortofrutta.auth.UserJwtAuthenticationConverter;
import it.tuonome.ortofrutta.company.CompanyContextFilter;
import it.tuonome.ortofrutta.company.CompanyRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Matrice dei permessi. Le regole si valutano in ordine: vale la prima che corrisponde.
     * I permessi appartengono ai ruoli (tabella roles): l'amministratore li assegna dall'applicazione.
     * "ACCESS" = utente attivo che non deve più cambiare la password: chi non lo ha può usare solo
     * /api/auth/me e /api/auth/change-password.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            UserJwtAuthenticationConverter jwtConverter,
                                            CompanyRepository companies,
                                            PlatformTransactionManager transactionManager,
                                            JdbcClient jdbc) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API stateless con token Bearer: niente cookie, niente CSRF
                .cors(cors -> { })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/register-employee",
                                "/api/auth/forgot-password", "/api/auth/reset-password",
                                "/actuator/health", "/actuator/health/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/companies").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/companies/register").permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/change-password").authenticated()

                        // amministrazione
                        .requestMatchers(HttpMethod.GET, "/api/users/role-options")
                                .hasAnyAuthority("USERS_READ", "USERS_APPROVE", "USERS_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/users/**")
                                .hasAnyAuthority("USERS_READ", "USERS_APPROVE", "USERS_MANAGE")
                        .requestMatchers(HttpMethod.POST, "/api/users/*/approve")
                                .hasAnyAuthority("USERS_APPROVE", "USERS_MANAGE")
                        .requestMatchers("/api/users/**").hasAuthority("USERS_MANAGE")
                        .requestMatchers("/api/roles/**").hasAuthority("ROLES_MANAGE")
                        .requestMatchers("/api/admin/lookups/**").hasAuthority("LOOKUPS_MANAGE")
                        .requestMatchers("/api/audit/**").hasAuthority("AUDIT_VIEW")

                        // prodotti e prezzi
                        .requestMatchers(HttpMethod.GET, "/api/catalog").hasAuthority("CATALOG_READ")
                        .requestMatchers(HttpMethod.GET, "/api/products/**").hasAuthority("PRODUCTS_READ")
                        .requestMatchers("/api/products/**").hasAuthority("PRODUCTS_WRITE")

                        // fornitori e ordini
                        .requestMatchers(HttpMethod.GET, "/api/suppliers/**")
                                .hasAnyAuthority("SUPPLIERS_READ", "SUPPLIERS_MANAGE")
                        .requestMatchers("/api/suppliers/**").hasAuthority("SUPPLIERS_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/supplier-orders", "/api/supplier-orders/*")
                                .hasAnyAuthority("SUPPLIER_ORDER_CREATE", "SUPPLIER_ORDERS_MANAGE")
                        .requestMatchers(HttpMethod.POST, "/api/supplier-orders")
                                .hasAnyAuthority("SUPPLIER_ORDER_CREATE", "SUPPLIER_ORDERS_MANAGE")
                        .requestMatchers("/api/supplier-orders/**").hasAuthority("SUPPLIER_ORDERS_MANAGE")

                        // magazzino
                        .requestMatchers(HttpMethod.GET, "/api/stock/lots", "/api/stock/movements")
                                .hasAuthority("STOCK_READ")
                        .requestMatchers("/api/stock/receipts").hasAuthority("STOCK_RECEIVE")
                        .requestMatchers("/api/stock/waste").hasAuthority("WASTE_CREATE")
                        .requestMatchers("/api/stock/count").hasAuthority("STOCK_RECEIVE")

                        // cassa
                        .requestMatchers(HttpMethod.POST, "/api/sales").hasAuthority("SALES_CREATE")
                        .requestMatchers(HttpMethod.POST, "/api/sales/*/void").hasAuthority("SALES_VOID")
                        .requestMatchers(HttpMethod.GET, "/api/sales/customer/**")
                                .hasAnyAuthority("CUSTOMERS_MANAGE", "RESERVATIONS_MANAGE", "REPORTS_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/sales", "/api/sales/*")
                                .hasAnyAuthority("SALES_CREATE", "SALES_VOID", "REPORTS_VIEW")

                        // clienti e prenotazioni
                        .requestMatchers(HttpMethod.DELETE, "/api/customers/**").hasAuthority("CUSTOMERS_DELETE")
                        .requestMatchers("/api/customers/**")
                                .hasAnyAuthority("CUSTOMERS_MANAGE", "RESERVATIONS_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/lookups").hasAuthority(UserJwtAuthenticationConverter.ACCESS)
                        .requestMatchers(HttpMethod.GET, "/api/customer-orders/mine").hasAuthority("CUSTOMER_ORDER_READ")
                        .requestMatchers(HttpMethod.POST, "/api/customer-orders/mine").hasAuthority("CUSTOMER_ORDER_CREATE")
                        .requestMatchers("/api/customer-orders/**").hasAuthority("RESERVATIONS_MANAGE")

                        // report; la dashboard è disponibile agli operatori, non agli account cliente
                        .requestMatchers("/api/reports/products", "/api/reports/products/export",
                                "/api/reports/corrispettivi", "/api/reports/cash-closing")
                                .hasAuthority("REPORTS_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/reports/dashboard")
                                .hasAnyAuthority("STOCK_READ", "SALES_CREATE", "REPORTS_VIEW", "SUPPLIER_ORDERS_MANAGE")
                        .requestMatchers("/api/reports/reorder-suggestions")
                                .hasAnyAuthority("SUPPLIER_ORDER_CREATE", "SUPPLIER_ORDERS_MANAGE", "REPORTS_VIEW")

                        // Le nuove rotte non devono diventare accessibili automaticamente a tutti gli utenti attivi.
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));
        http.addFilterBefore(new CompanyContextFilter(companies, new TransactionTemplate(transactionManager), jdbc),
                BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") String configuredOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", CompanyContextFilter.HEADER));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    SecretKey jwtSecretKey(AppProperties props) {
        byte[] bytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret deve avere almeno 32 caratteri");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
