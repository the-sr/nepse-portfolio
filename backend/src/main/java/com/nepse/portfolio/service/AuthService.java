package com.nepse.portfolio.service;

import com.nepse.portfolio.domain.RefreshToken;
import com.nepse.portfolio.dto.auth.AuthDtos;
import com.nepse.portfolio.repository.RefreshTokenRepository;

import com.nepse.portfolio.common.ApiException;
import com.nepse.portfolio.security.JwtService;
import com.nepse.portfolio.domain.Role;
import com.nepse.portfolio.domain.User;
import com.nepse.portfolio.repository.UserRepository;
import com.nepse.portfolio.domain.UserSettings;
import com.nepse.portfolio.repository.UserSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Duration refreshTtl;

    public AuthService(UserRepository userRepository,
                       UserSettingsRepository settingsRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       @Value("${app.jwt.refresh-token-days:14}") long refreshDays) {
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTtl = Duration.ofDays(refreshDays);
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = userRepository.save(new User(
                email,
                passwordEncoder.encode(request.password()),
                request.name().trim(),
                Role.USER
        ));
        settingsRepository.save(new UserSettings(user.getId()));
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(String rawToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenAndRevokedFalse(rawToken)
                .orElseThrow(() -> ApiException.unauthorized("Refresh token is invalid"));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.revoke();
            throw ApiException.unauthorized("Refresh token has expired");
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        stored.revoke();
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenAndRevokedFalse(rawToken).ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserDto currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.unauthorized("Not signed in"));
        return AuthDtos.UserDto.from(user);
    }

    private AuthDtos.AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        refreshTokenRepository.save(new RefreshToken(user.getId(), refreshToken, Instant.now().plus(refreshTtl)));
        return new AuthDtos.AuthResponse(accessToken, refreshToken, jwtService.getAccessTtlSeconds(),
                AuthDtos.UserDto.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
