package work.managerbe.oauthaccount.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.oauthaccount.domain.PendingRegistration;
import work.managerbe.oauthaccount.dto.request.OAuthUserInfo;
import work.managerbe.oauthaccount.dto.response.PendingRegistrationResponse;
import work.managerbe.oauthaccount.repository.PendingRegistrationRepository;
import work.managerbe.oauthaccount.repository.RegistrationTermRepository;
import work.managerbe.oauthaccount.dto.response.RegistrationTermResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/** 임시 가입 전용 무작위 토큰을 발급하고 서버에는 해시와 소셜 프로필만 저장한다. */
@Service
@RequiredArgsConstructor
@Transactional
public class PendingRegistrationService {
    public static final Duration EXPIRATION = Duration.ofMinutes(10);
    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final long CLEANUP_INTERVAL_MILLIS = 600_000;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final PendingRegistrationRepository repository;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final RegistrationTermRepository termRepository;

    /** 같은 브라우저의 이전 임시 토큰을 폐기하고 새 인증 결과를 10분간 보관한다. */
    public String issue(OAuthUserInfo info, String previousToken) {
        Objects.requireNonNull(info, "소셜 인증 정보가 필요합니다.");
        revoke(previousToken);
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(PendingRegistration.create(info, hash(token), LocalDateTime.now(clock).plus(EXPIRATION)));
        return token;
    }

    /** 토큰이 없거나 만료·위조된 경우 401로 처리하며 조회로 만료 시간을 연장하지 않는다. */
    @Transactional(readOnly = true)
    public PendingRegistrationResponse get(String token) {
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("임시 가입 정보가 없습니다.");
        }
        return repository.findByTokenHashAndExpiresAtAfter(hash(token), LocalDateTime.now(clock))
                .map(pending -> PendingRegistrationResponse.from(pending, termRepository.findByActiveTrueOrderByCodeAsc()
                        .stream().map(RegistrationTermResponse::from).toList()))
                .orElseThrow(() -> new BadCredentialsException("임시 가입 정보가 만료되었거나 유효하지 않습니다."));
    }

    /** 가입 완료용 행을 잠근 뒤 현재 시각으로 만료를 다시 확인한다. */
    public PendingRegistration lock(String token) {
        if (token == null || token.isBlank()) throw new BadCredentialsException("임시 가입 정보가 없습니다.");
        return repository.findByTokenHash(hash(token))
                .filter(pending -> pending.getExpiresAt().isAfter(LocalDateTime.now(clock)))
                .orElseThrow(() -> new BadCredentialsException("유효한 임시 가입 정보가 없습니다."));
    }

    /** 취소나 기존 회원 로그인 시 이전 임시 가입 정보를 삭제한다. */
    public void revoke(String token) {
        if (token != null && !token.isBlank()) repository.deleteByTokenHash(hash(token));
    }

    /** 만료 시각이 지난 개인정보를 10분 간격으로 삭제한다. */
    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MILLIS)
    public long cleanup() {
        return repository.deleteByExpiresAtLessThanEqual(LocalDateTime.now(clock));
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("토큰 해시 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
