package work.managerbe.card.dto;

import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import work.managerbe.card.dto.request.CardUpdateRequest;
import static org.assertj.core.api.Assertions.assertThat;

/** 실제 JSON 역직렬화로 부분 수정의 입력 검증과 날짜 생략·삭제 구분을 확인한다. */
class CardUpdateRequestTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    /** 빈 요청과 필드의 잘못된 경계값은 거절한다. */
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"title\":\" \"}", "{\"sortOrder\":-1}",
            "{\"boardId\":0}", "{\"boardId\":-1}", "{\"title\":null}"})
    void 유효하지_않은_입력을_거절한다(String json) throws Exception {
        // given
        CardUpdateRequest request = MAPPER.readValue(json, CardUpdateRequest.class);
        // when / then
        assertThat(request.isValid()).isFalse();
    }

    /** 각 수정 필드를 독립적으로 전달할 수 있으며 0 기반 위치와 빈 본문을 허용한다. */
    @ParameterizedTest
    @ValueSource(strings = {"{\"title\":\"제목\"}", "{\"content\":\"\"}", "{\"boardId\":1}",
            "{\"sortOrder\":0}", "{\"status\":\"DONE\"}", "{\"startDate\":null}", "{\"endDate\":null}"})
    void 유효한_부분_수정_입력을_허용한다(String json) throws Exception {
        // given
        CardUpdateRequest request = MAPPER.readValue(json, CardUpdateRequest.class);
        // when / then
        assertThat(request.isValid()).isTrue();
    }

    /** 생략과 명시적 null을 구분하고 날짜 문자열을 날짜 값으로 읽는다. */
    @Test
    void 날짜_생략과_삭제와_설정을_구분한다() throws Exception {
        // given
        CardUpdateRequest omitted = MAPPER.readValue("{\"title\":\"제목\"}", CardUpdateRequest.class);
        CardUpdateRequest cleared = MAPPER.readValue("{\"startDate\":null,\"endDate\":null}", CardUpdateRequest.class);
        CardUpdateRequest dated = MAPPER.readValue("{\"startDate\":\"2026-09-11\",\"endDate\":\"2026-09-12\"}", CardUpdateRequest.class);
        // when / then
        assertThat(omitted.isStartDateProvided()).isFalse();
        assertThat(omitted.isEndDateProvided()).isFalse();
        assertThat(cleared.isStartDateProvided()).isTrue();
        assertThat(cleared.isEndDateProvided()).isTrue();
        assertThat(cleared.getStartDate()).isNull();
        assertThat(cleared.getEndDate()).isNull();
        assertThat(dated.isStartDateProvided()).isTrue();
        assertThat(dated.isEndDateProvided()).isTrue();
        assertThat(dated.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 11));
        assertThat(dated.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 12));
    }
}
