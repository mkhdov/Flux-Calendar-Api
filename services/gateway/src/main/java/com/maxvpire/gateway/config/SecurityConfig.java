package com.maxvpire.gateway.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.Base64;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/user-management/v3/api-docs/**",
                                "/user-management/swagger-ui/**",
                                "/user-management/swagger-ui.html",
                                "/calendars/v3/api-docs/**",
                                "/calendars/swagger-ui/**",
                                "/calendars/swagger-ui.html",
                                "/auth/**",
                                "/users/add",
                                "/users/keycloak/**",
                                "/api/users/login/**",
                                "/api/auth/**",
                                "/api/devices/**",
                                "/login",
                                "/oauth2/**",
                                "/users/sync",
                                "/zoom/connect/**",
                                "/zoom/callback")
                        .permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        return http.build();
    }

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA512");
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS512).build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        if ("*".equals(frontendUrl)) {
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else if (frontendUrl != null && frontendUrl.contains(",")) {
            List<String> origins = List.of(frontendUrl.split("\\s*,\\s*"));
            configuration.setAllowedOrigins(origins);
        } else {
            configuration.setAllowedOrigins(List.of(frontendUrl));
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(List.of("*")); // Allow all headers dynamically
        configuration.setExposedHeaders(List.of("Authorization", "X-User-Id"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public org.springframework.web.cors.reactive.CorsWebFilter corsWebFilter() {
        return new org.springframework.web.cors.reactive.CorsWebFilter(corsConfigurationSource());
    }
}
