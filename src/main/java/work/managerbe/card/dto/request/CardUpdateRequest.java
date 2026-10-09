package work.managerbe.card.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.time.LocalDate;
import lombok.Getter;
import work.managerbe.card.domain.CardStatus;

/** 생략한 필드는 유지하고 명시한 null은 날짜 삭제에만 허용한다. 순서는 대상 상태 목록의 0 기반 위치다. */
@Getter
public class CardUpdateRequest {
    @JsonSetter(nulls = Nulls.FAIL) private String title;

    @JsonSetter(nulls = Nulls.FAIL) private String content;

    @JsonSetter(nulls = Nulls.FAIL) private Long boardId;

    @JsonSetter(nulls = Nulls.FAIL) private CardStatus status;

    @JsonSetter(nulls = Nulls.FAIL) private Integer sortOrder;

    private LocalDate startDate;

    private LocalDate endDate;

    @JsonIgnore private boolean startDateProvided;

    @JsonIgnore private boolean endDateProvided;

    /** 날짜가 생략된 경우와 명시적으로 삭제된 경우를 구분한다. */
    @JsonSetter("startDate")
    public void readStartDate(LocalDate value) {
        startDate = value;
        startDateProvided = true;
    }

    /** 종료일 null을 전달하면 기존 종료일을 삭제한다. */
    @JsonSetter("endDate")
    public void readEndDate(LocalDate value) {
        endDate = value;
        endDateProvided = true;
    }

    /** 서비스 직접 호출에도 제목, 보드, 위치와 빈 요청을 검증한다. 날짜 조합은 기존 값과 합쳐 검증한다. */
    @JsonIgnore
    public boolean isValid() {
        return (title == null || !title.isBlank()) && (boardId == null || boardId > 0)
                && (sortOrder == null || sortOrder >= 0)
                && (title != null || content != null || boardId != null || status != null
                    || sortOrder != null || startDateProvided || endDateProvided);
    }
}
