package com.taskflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * JWT signing and lifetime configuration.
 *
 * @param secret          HMAC signing secret, at least 32 bytes
 * @param issuer          value written to the {@code iss} claim
 * @param accessTokenTtl  short-lived access token lifetime
 * @param refreshTokenTtl refresh token lifetime
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("taskflow") String issuer,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl
) {
    public JwtProperties {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("security.jwt.secret must be at least 32 bytes long");
        }
    }
}
