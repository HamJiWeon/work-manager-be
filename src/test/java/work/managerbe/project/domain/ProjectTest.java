package work.managerbe.project.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectTest {

    @Test
    @DisplayName("프로젝트를 생성하면 다음 카드 번호는 1이다.")
    void 프로젝트_생성() {
        // given
        String code = "WORK_test";
        String name = "업무 관리 서비스";
        String description = "팀 프로젝트와 업무 관리";

        // when
        Project project = Project.create(code, name, description);

        // then
        assertThat(project.getCode()).isEqualTo(code);
        assertThat(project.getName()).isEqualTo(name);
        assertThat(project.getDescription()).isEqualTo(description);
        assertThat(project.getNextCardNumber()).isEqualTo(1L);
    }
}