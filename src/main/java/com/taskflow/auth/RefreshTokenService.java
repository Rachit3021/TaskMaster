package com.taskflow.auth;

import com.taskflow.common.error.UnauthorizedException;
import com.taskflow.config.JwtProperties;
import com.taskflow.user.User;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/** Creates, rotates and revokes opaque refresh tokens. */
@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties jwtProperties) {
        this.repository = repository;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public String issue(User user, String userAgent, String ipAddress) {
        byte[] randomBytes = new byte[48];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant expiresAt = Instant.now().plus(jwtProperties.refreshTokenTtl());
        repository.save(new RefreshToken(user, hash(rawToken), expiresAt,
                truncate(userAgent), truncate(ipAddress)));
        return rawToken;
    }

    @Transactional
    public Rotation rotate(String rawToken, String userAgent, String ipAddress) {
        RefreshToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (token.isRevoked()) {
            throw new UnauthorizedException("Refresh token has already been used or revoked");
        }
        if (!token.getExpiresAt().isAfter(Instant.now())) {
            throw new UnauthorizedException("Refresh token has expired");
        }
        token.setRevoked(true);
        User user = token.getUser();
        String newToken = issue(user, userAgent, ipAddress);
        return new Rotation(user, newToken);
    }

    /** Idempotent revocation; unknown tokens are ignored to avoid token probing. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> token.setRevoked(true));
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId);
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        repository.deleteExpiredBefore(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    public record Rotation(User user, String refreshToken) {
    }
}
