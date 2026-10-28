package com.lifetracker.auth;

import com.lifetracker.auth.dto.AuthRequest;
import com.lifetracker.auth.dto.AuthResponse;
import com.lifetracker.auth.dto.RefreshResult;
import com.lifetracker.common.ConflictException;
import com.lifetracker.common.UnauthorizedException;
import com.lifetracker.config.JwtService;
import com.lifetracker.user.User;
import com.lifetracker.user.UserProfile;
import com.lifetracker.user.UserProfileRepository;
import com.lifetracker.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTtlDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.refresh-ttl-days:30}") long refreshTtlDays) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTtlDays = refreshTtlDays;
    }

    @Transactional
    public AuthResponse signup(AuthRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(cleanEmail)) {
            throw new ConflictException("Email already registered");
        }

        User user = new User(cleanEmail, passwordEncoder.encode(request.getPassword()));
        user = userRepository.save(user);

        UserProfile profile = new UserProfile(user.getId());
        userProfileRepository.save(profile);

        UUID familyId = UUID.randomUUID();
        String rawRefreshToken = generateOpaqueToken();
        String tokenHash = sha256Hex(rawRefreshToken);
        Instant expiresAt = Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS);

        RefreshToken refreshToken = new RefreshToken(user.getId(), familyId, tokenHash, expiresAt);
        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtService.createAccessToken(user.getId());
        return new AuthResponse(accessToken, rawRefreshToken, jwtService.getAccessTtlSeconds());
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(cleanEmail);

        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            throw new UnauthorizedException("Invalid credentials");
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        UUID familyId = UUID.randomUUID();
        String rawRefreshToken = generateOpaqueToken();
        String tokenHash = sha256Hex(rawRefreshToken);
        Instant expiresAt = Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS);

        RefreshToken refreshToken = new RefreshToken(user.getId(), familyId, tokenHash, expiresAt);
        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtService.createAccessToken(user.getId());
        return new AuthResponse(accessToken, rawRefreshToken, jwtService.getAccessTtlSeconds());
    }

    @Transactional
    public RefreshResult refresh(String rawRefreshToken) {
        String tokenHash = sha256Hex(rawRefreshToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            return RefreshResult.rejected();
        }

        RefreshToken row = tokenOpt.get();
        Instant now = Instant.now();

        // Reuse detected: Token was already rotated
        if (row.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(row.getFamilyId(), now);
            return RefreshResult.rejected();
        }

        // Token expired
        if (row.getExpiresAt().isBefore(now)) {
            return RefreshResult.rejected();
        }

        // Verify user is still active
        Optional<User> userOpt = userRepository.findById(row.getUserId());
        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            return RefreshResult.rejected();
        }

        // Valid refresh: Rotate token within the same family
        row.setRevokedAt(now);

        String newRawRefreshToken = generateOpaqueToken();
        String newTokenHash = sha256Hex(newRawRefreshToken);
        Instant newExpiresAt = now.plus(refreshTtlDays, ChronoUnit.DAYS);

        RefreshToken newToken = new RefreshToken(row.getUserId(), row.getFamilyId(), newTokenHash, newExpiresAt);
        newToken = refreshTokenRepository.save(newToken);

        row.setReplacedBy(newToken.getId());
        refreshTokenRepository.save(row);

        String newAccessToken = jwtService.createAccessToken(row.getUserId());
        AuthResponse response = new AuthResponse(newAccessToken, newRawRefreshToken, jwtService.getAccessTtlSeconds());
        return RefreshResult.ok(response);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.trim().isEmpty()) {
            return;
        }
        String tokenHash = sha256Hex(rawRefreshToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        tokenOpt.ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
