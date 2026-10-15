package work.managerbe.oauthaccount.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.global.exception.CommonException;
import work.managerbe.global.exception.ErrorCode;
import work.managerbe.global.security.TokenPair;
import work.managerbe.oauthaccount.domain.*;
import work.managerbe.oauthaccount.dto.request.RegistrationCompleteRequest;
import work.managerbe.oauthaccount.repository.*;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** 가입 검증부터 회원·동의·로그인 토큰 저장과 임시 상태 소비까지 한 트랜잭션으로 처리한다. */
@Service
@RequiredArgsConstructor
@Transactional
public class RegistrationCompletionService {
    private final PendingRegistrationService pendingService;
    private final PendingRegistrationRepository pendingRepository;
    private final RegistrationTermRepository termRepository;
    private final RegistrationConsentRepository consentRepository;
    private final UserRepository userRepository;
    private final OauthAccountRepository accountRepository;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    /** 임시 토큰을 잠가 중복 제출을 막고 서버 약관 버전으로 동의를 검증한 뒤 가입한다. */
    public TokenPair complete(String token, RegistrationCompleteRequest request) {
        PendingRegistration pending = pendingService.lock(token);
        List<RegistrationTerm> terms = termRepository.lockActiveTerms();
        Map<String, Boolean> choices = validate(terms, request);
        if (accountRepository.findByProviderAndProviderUserId(pending.getProvider(), pending.getProviderUserId()).isPresent()) {
            throw CommonException.of(ErrorCode.CONFLICT);
        }
        var user = userRepository.save(User.create(pending.getName(), pending.getEmail(), pending.getProfileImageUrl()));
        OauthAccount account;
        try {
            account = accountRepository.saveAndFlush(OauthAccount.create(user, pending.getProvider(), pending.getProviderUserId()));
        } catch (DataIntegrityViolationException exception) {
            throw CommonException.of(ErrorCode.CONFLICT);
        }
        LocalDateTime recordedAt = LocalDateTime.now(clock);
        consentRepository.saveAll(terms.stream().map(term -> RegistrationConsent.create(
                account, term, choices.getOrDefault(term.getCode(), false), recordedAt)).toList());
        TokenPair tokens = refreshTokenService.issue(user);
        pendingRepository.delete(pending);
        return tokens;
    }

    private Map<String, Boolean> validate(List<RegistrationTerm> terms, RegistrationCompleteRequest request) {
        var codes = new HashSet<String>();
        if (terms.stream().noneMatch(RegistrationTerm::isRequired)
                || terms.stream().anyMatch(term -> !codes.add(term.getCode()) || term.getContent().isBlank())) {
            throw CommonException.of(ErrorCode.REGISTRATION_TERMS_UNAVAILABLE);
        }
        if (request == null || request.consents() == null || request.consents().isEmpty()) throw invalid();
        var termByCode = new HashMap<String, RegistrationTerm>();
        terms.forEach(term -> termByCode.put(term.getCode(), term));
        var choices = new HashMap<String, Boolean>();
        for (var consent : request.consents()) {
            if (consent == null || consent.id() == null || consent.version() == null || consent.agreed() == null) throw invalid();
            var term = termByCode.get(consent.id());
            if (term == null || !term.getVersion().equals(consent.version())
                    || choices.putIfAbsent(consent.id(), consent.agreed()) != null) throw invalid();
        }
        if (terms.stream().anyMatch(term -> term.isRequired() && !Boolean.TRUE.equals(choices.get(term.getCode())))) throw invalid();
        return choices;
    }

    private CommonException invalid() { return CommonException.of(ErrorCode.INVALID_REQUEST); }
}
