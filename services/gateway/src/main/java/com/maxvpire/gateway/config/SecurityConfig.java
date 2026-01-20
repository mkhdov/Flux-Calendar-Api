package com.maxvpire.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

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
                                                                "/users/sync")
                                                .permitAll()
                                                .anyExchange().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
                                .csrf(ServerHttpSecurity.CsrfSpec::disable);

                return http.build();
        }
}