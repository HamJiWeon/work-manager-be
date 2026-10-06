package work.managerbe.member.dto;

import jakarta.validation.constraints.NotBlank;

/** 역할 변경에 필요한 값을 검증하며 소유권 이전은 서비스에서 거절한다. */
public record MemberUpdateRequest(@NotBlank String role) {
}
