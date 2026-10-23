package work.managerbe.member.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import work.managerbe.member.domain.MemberRole;

/** 소유권 이전을 제외하고 변경할 관리자 또는 일반 멤버 역할을 받는다. */
public record MemberUpdateRequest(@NotNull MemberRole role) {
    /** HTTP 요청과 서비스 직접 호출에서 OWNER 부여와 누락된 역할을 거절한다. */
    @JsonIgnore
    @AssertTrue(message = "변경할 역할은 ADMIN 또는 MEMBER여야 합니다.")
    public boolean isValid() {
        return role == MemberRole.ADMIN || role == MemberRole.MEMBER;
    }
}
