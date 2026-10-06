package work.managerbe.card.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import work.managerbe.card.domain.CardStatus;

/**
 * 카드 내용, 상태, 담당자와 경로 대조용 프로젝트·보드 ID 및 선택 일정을 받는다.
 */
public record CardCreateRequest(
        @NotBlank @Size(max = CardCreateRequest.MAX_USERNAME_LENGTH) String username,
        @NotBlank String title,
        @NotNull String content,
        @NotNull CardStatus status,
        @NotNull @Positive Long memberId,
        @NotNull @Positive Long projectId,
        @NotNull @Positive Long boardId,
        LocalDate startDate,
        LocalDate endDate
) {
    private static final int MAX_USERNAME_LENGTH = 255;

    /**
     * 서비스 직접 호출에서도 필수값과 양수 ID, 일정 순서를 검증한다.
     */
    public boolean isValid() {
        return username != null && !username.isBlank() && username.length() <= MAX_USERNAME_LENGTH
                && title != null && !title.isBlank() && content != null && status != null
                && memberId != null && memberId > 0 && projectId != null && projectId > 0
                && boardId != null && boardId > 0
                && (startDate == null || endDate == null || !endDate.isBefore(startDate));
    }
}
