package com.ticketfilms.api_manager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(exchanges -> exchanges
                .pathMatchers(HttpMethod.GET, "/api/cartelera/**").permitAll()
                .pathMatchers("/actuator/**").permitAll()
                .pathMatchers("/api/asientos/**").authenticated()
                .pathMatchers("/api/boletos/**").authenticated()
                .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(this::logAndConvert))
                .authenticationEntryPoint((exchange, ex) -> {
                    log.warn("401 UNAUTHORIZED - path={} reason={}",
                            exchange.getRequest().getPath(), ex.getMessage());
                    return Mono.fromRunnable(()
                            -> exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED));
                })
                .accessDeniedHandler((exchange, denied) -> {
                    log.warn("403 FORBIDDEN - path={} reason={}",
                            exchange.getRequest().getPath(), denied.getMessage());
                    return Mono.fromRunnable(()
                            -> exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN));
                })
                );

        return http.build();
    }

    private Mono<AbstractAuthenticationToken> logAndConvert(Jwt jwt) {
        log.info("JWT valido - subject={} issuer={}", jwt.getSubject(), jwt.getIssuer());
        return Mono.just(new JwtAuthenticationToken(jwt));
    }
}
