package work.managerbe.card.dto;

import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import work.managerbe.card.domain.CardStatus;
import work.managerbe.card.dto.request.CardCreateRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 필수값과 ID 경계값을 각각 검증하고 이름 길이 및 선택 일정의 허용 범위를 확인한다.
 */
class CardCreateRequestTest {
    private static final int MAX_USERNAME_LENGTH = 255;
    private static final LocalDate START_DATE = LocalDate.of(2026, 10, 5);

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void 필수값이나_ID_일정이_잘못되면_유효하지_않다(CardCreateRequest request) {
        // given / when
        boolean valid = request.isValid();
        // then
        assertThat(valid).isFalse();
    }

    @ParameterizedTest
    @MethodSource("validRequests")
    void 이름_최대길이와_선택_일정_조합을_허용한다(CardCreateRequest request) {
        // given / when
        boolean valid = request.isValid();
        // then
        assertThat(valid).isTrue();
    }

    private static Stream<CardCreateRequest> invalidRequests() {
        return Stream.of(
                new CardCreateRequest(null, "제목", "", CardStatus.IN_PROGRESS, 1L, 1L, 1L, null, null),
                new CardCreateRequest("", "제목", "", CardStatus.IN_PROGRESS, 1L, 1L, 1L, null, null),
                new CardCreateRequest(" ", "제목", "", CardStatus.IN_PROGRESS, 1L, 1L, 1L, null, null),
                new CardCreateRequest("가".repeat(MAX_USERNAME_LENGTH + 1), "제목", "", CardStatus.IN_PROGRESS, 1L, 1L, 1L, null, null),
                new CardCreateRequest("담당자", null, "", CardStatus.IN_PROGRESS, 1L, 1L, 1L, null, null),
                new CardCreateRequest("담당자", "", "", CardStatus.IN_PROGRESS, 1L, 1L, 1L, null, null),
                new CardCreateRequest("담당자", " ", "", CardStatus.IN_PROGRESS, 1L, 1L, 1L, null, null),
                new CardCreateRequest("담당자", "제목", null, CardStatus.IN_PROGRESS, 1L, 1L, 1L, null, null),
                new CardCreateRequest("담당자", "제목", "", null, 1L, 1L, 1L, null, null),
                new CardCreateRequest("담당자", "제목", "", CardStatus.IN_PROGRESS, null, 1L, 1L, null, null),
                new CardCreateRequest("담당자", "제목", "", CardStatus.IN_PROGRESS, 0L, 1L, 1L, null, null),
                new CardCreateRequest("담당자", "제목", "", CardStatus.IN_PROGRESS, -1L, 1L, 1L, null, null),
                new CardCreateRequest("담당자", "제목", "", CardStatus.IN_PROGRESS, 1L, null, 1L, null, null),
                new CardCreateRequest("담당자", "제목", "", CardStatus.IN_PROGRESS, 1L, 0L, 1L, null, null),
                new CardCreateRequest("담당자", "제목", "", CardStatus.IN_PROGRESS, 1L, -1L, 1L, null, null),
                new CardCreateRequest("담당자", "제목", "", CardStatus.IN_PROGRESS, 1L, 1L, null, null, null),
                new CardCreateRequest("담당자", "제목", "", CardStatus.IN_PROGRESS, 1L, 1L, 0L, null, null),
                new CardCreateRequest("담당자", "제목", "", CardStatus.IN_PROGRESS, 1L, 1L, -1L, null, null),
                new CardCreateRequest("담당자", "제목", "", CardStatus.IN_PROGRESS, 1L, 1L, 1L, START_DATE, START_DATE.minusDays(1)));
    }

    private static Stream<CardCreateRequest> validRequests() {
        return Stream.of(
                new CardCreateRequest("가".repeat(MAX_USERNAME_LENGTH), "제목", "", CardStatus.IN_PROGRESS, 1L, 1L, 1L, null, null),
                new CardCreateRequest("가".repeat(MAX_USERNAME_LENGTH), "제목", "", CardStatus.IN_PROGRESS, 1L, 1L, 1L, START_DATE, null),
                new CardCreateRequest("가".repeat(MAX_USERNAME_LENGTH), "제목", "", CardStatus.IN_PROGRESS, 1L, 1L, 1L, null, START_DATE),
                new CardCreateRequest("가".repeat(MAX_USERNAME_LENGTH), "제목", "", CardStatus.IN_PROGRESS, 1L, 1L, 1L, START_DATE, START_DATE),
                new CardCreateRequest("가".repeat(MAX_USERNAME_LENGTH), "제목", "", CardStatus.IN_PROGRESS, 1L, 1L, 1L, START_DATE, START_DATE.plusDays(1)));
    }
}
