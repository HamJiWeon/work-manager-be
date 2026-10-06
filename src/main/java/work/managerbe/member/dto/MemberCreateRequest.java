package work.managerbe.member.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** 사용자 ID와 선택적인 역할을 받아 누락된 역할을 MEMBER로 설정한다. */
public record MemberCreateRequest(@NotNull UUID userId, String role) {
    public MemberCreateRequest {
        if (role == null) {
            role = "MEMBER";
        }
    }
}
