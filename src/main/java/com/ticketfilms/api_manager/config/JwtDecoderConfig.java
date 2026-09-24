package com.ticketfilms.api_manager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class JwtDecoderConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtDecoderConfig.class);

    @Value("${cognito.issuer-uri}")
    private String issuerUri;

    @Value("${cognito.client-id}")
    private String expectedClientId;

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        log.info("Construyendo ReactiveJwtDecoder custom: issuer={} clientId={}",
                issuerUri, expectedClientId);

        NimbusReactiveJwtDecoder decoder
                = (NimbusReactiveJwtDecoder) ReactiveJwtDecoders.fromIssuerLocation(issuerUri);

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = jwt -> {
            String clientId = jwt.getClaimAsString("client_id");
            if (expectedClientId.equals(clientId)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "El client_id del token no coincide: " + clientId, null));
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }
}