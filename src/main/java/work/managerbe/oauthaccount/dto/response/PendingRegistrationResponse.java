package work.managerbe.oauthaccount.dto.response;

import work.managerbe.oauthaccount.domain.OAuthProvider;
import work.managerbe.oauthaccount.domain.PendingRegistration;

import java.time.LocalDateTime;

/** 소셜 고유 ID와 토큰 원문을 노출하지 않고 가입 안내에 필요한 정보만 반환한다. */
public record PendingRegistrationResponse(String name, OAuthProvider provider, LocalDateTime expiresAt) {
    /** 임시 가입 엔티티에서 공개할 정보만 선택한다. */
    public static PendingRegistrationResponse from(PendingRegistration pending) {
        return new PendingRegistrationResponse(pending.getName(), pending.getProvider(), pending.getExpiresAt());
    }
}
