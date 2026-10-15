package work.managerbe.oauthaccount.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import work.managerbe.oauthaccount.dto.request.RegistrationCompleteRequest;
import work.managerbe.oauthaccount.service.RegistrationCompletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import work.managerbe.global.security.AuthCookieService;
import work.managerbe.oauthaccount.dto.response.PendingRegistrationResponse;
import work.managerbe.oauthaccount.service.PendingRegistrationService;

/** 정식 로그인 권한 없이 임시 가입 쿠키로 자신의 가입 상태만 조회하거나 취소한다. */
@RestController
@RequestMapping("/auth/registration")
@RequiredArgsConstructor
public class RegistrationController {
    private final PendingRegistrationService service;
    private final AuthCookieService cookieService;
    private final RegistrationCompletionService completionService;

    /** 쿠키 소유자의 유효한 임시 가입 정보를 조회하고 응답 캐시를 금지한다. */
    @GetMapping
    public PendingRegistrationResponse get(
            @CookieValue(name = AuthCookieService.PENDING_REGISTRATION_COOKIE, required = false) String token,
            HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return service.get(token);
    }

    /** 필수 동의 검증과 가입 커밋 후 로그인 쿠키를 발급하고 임시 쿠키를 제거한다. */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(
            @CookieValue(name = AuthCookieService.PENDING_REGISTRATION_COOKIE, required = false) String token,
            @Valid @RequestBody RegistrationCompleteRequest request,
            HttpServletResponse response) {
        var tokens = completionService.complete(token, request);
        cookieService.addRefreshToken(response, tokens.refreshToken());
        cookieService.deletePendingRegistration(response);
    }

    /** 임시 가입을 취소하고 서버 정보와 브라우저 쿠키를 제거한다. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @CookieValue(name = AuthCookieService.PENDING_REGISTRATION_COOKIE, required = false) String token,
            HttpServletResponse response) {
        service.revoke(token);
        cookieService.deletePendingRegistration(response);
    }
}
