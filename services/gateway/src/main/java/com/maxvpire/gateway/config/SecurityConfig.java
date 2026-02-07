package com.maxvpire.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

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
                                                                "/login",
                                                                "/oauth2/**",
                                                                "/users/sync",
                                                                "/zoom/connect/**",
                                                                "/zoom/callback")
                                                .permitAll()
                                                .anyExchange().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
                                .csrf(ServerHttpSecurity.CsrfSpec::disable);

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
}