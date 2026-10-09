package work.managerbe.member.dto.request;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import work.managerbe.member.domain.MemberRole;
import java.util.UUID;

/** 추가할 사용자를 받으며 생략된 역할은 일반 멤버로 설정한다. */
public record MemberCreateRequest(
        @NotNull UUID userId,
        MemberRole role
) {
    private static final MemberRole DEFAULT_ROLE = MemberRole.MEMBER;

    /** 역할이 없으면 기본값을 적용한다. */
    public MemberCreateRequest {
        if (role == null) {
            role = DEFAULT_ROLE;
        }
    }

    /** 서비스 직접 호출에서도 사용자 ID와 허용된 역할을 검증한다. */
    @JsonIgnore
    @AssertTrue(message = "추가할 사용자와 MEMBER 역할을 지정해야 합니다.")
    public boolean isValid() {
        return userId != null && role == DEFAULT_ROLE;
    }
}
