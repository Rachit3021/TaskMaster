package com.taskflow.auth;

import com.taskflow.auth.dto.AuthResponse;
import com.taskflow.auth.dto.LoginRequest;
import com.taskflow.auth.dto.RefreshTokenRequest;
import com.taskflow.auth.dto.RegisterRequest;
import com.taskflow.common.error.BadRequestException;
import com.taskflow.common.error.ConflictException;
import com.taskflow.common.error.UnauthorizedException;
import com.taskflow.security.JwtService;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import com.taskflow.user.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Registration, login and token lifecycle. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()));
        userRepository.save(user);
        return issueTokens(user, httpRequest);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        if (!user.isEnabled()) {
            throw new UnauthorizedException("This account has been disabled");
        }
        return issueTokens(user, httpRequest);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(
                request.refreshToken(), httpRequest.getHeader("User-Agent"), clientIp(httpRequest));
        User user = rotation.user();
        if (!user.isEnabled()) {
            throw new UnauthorizedException("This account has been disabled");
        }
        String accessToken = jwtService.generateAccessToken(user);
        return AuthResponse.of(accessToken, rotation.refreshToken(),
                jwtService.accessTokenTtlSeconds(), UserResponse.from(user));
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional
    public void logoutEverywhere(java.util.UUID userId) {
        if (userId == null) {
            throw new BadRequestException("Authentication is required");
        }
        refreshTokenService.revokeAllForUser(userId);
    }

    private AuthResponse issueTokens(User user, HttpServletRequest httpRequest) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(
                user, httpRequest.getHeader("User-Agent"), clientIp(httpRequest));
        return AuthResponse.of(accessToken, refreshToken,
                jwtService.accessTokenTtlSeconds(), UserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
