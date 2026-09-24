package work.managerbe.oauthaccount.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.global.security.JwtProperties;
import work.managerbe.global.security.JwtTokenService;
import work.managerbe.global.security.TokenPair;
import work.managerbe.oauthaccount.domain.RefreshToken;
import work.managerbe.oauthaccount.repository.RefreshTokenRepository;
import work.managerbe.user.domain.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Refresh Token 원문은 한 번만 발급하고 해시를 저장해 재발급 시 기존 토큰을 회전한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties properties;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public TokenPair issue(User user) {
        String refreshToken = generateRefreshToken();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plus(properties.refreshTokenExpiration());
        refreshTokenRepository.save(RefreshToken.create(user, hash(refreshToken), expiresAt));
        return new TokenPair(jwtTokenService.createAccessToken(user.getId()), refreshToken);
    }

    public TokenPair rotate(String refreshToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(token -> token.isUsableAt(now))
                .orElseThrow(() -> new BadCredentialsException("유효하지 않은 Refresh Token입니다."));
        storedToken.revoke(now);
        return issue(storedToken.getUser());
    }

    public void revoke(String refreshToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .filter(token -> token.isUsableAt(now))
                .ifPresent(token -> token.revoke(now));
    }

    private String generateRefreshToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Refresh Token 해시 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
