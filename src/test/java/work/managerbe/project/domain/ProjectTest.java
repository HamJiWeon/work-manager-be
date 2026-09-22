package work.managerbe.project.domain;

import org.junit.jupiter.api.Test;
import work.managerbe.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로젝트 팩터리가 생성자와 코드 및 초기 카드 번호를 보존하는지 검증한다.
 */
class ProjectTest {
    @Test
    void 프로젝트_생성시_생성자와_코드_및_초기값을_설정한다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);

        // when
        Project project = Project.create(creator, "WORK", "업무 관리", "설명");

        // then
        assertThat(project.getCreator()).isSameAs(creator);
        assertThat(project.getCode()).isEqualTo("WORK");
        assertThat(project.getName()).isEqualTo("업무 관리");
        assertThat(project.getDescription()).isEqualTo("설명");
        assertThat(project.getNextCardNumber()).isEqualTo(1L);
        assertThat(project.getBoards()).isEmpty();
    }

    @Test
    void 이름만_전달하면_설명은_유지한다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);
        Project project = Project.create(creator, "WORK", "기존 이름", "설명");

        // when
        project.update("변경된 이름", null);

        // then
        assertThat(project.getName()).isEqualTo("변경된 이름");
        assertThat(project.getDescription()).isEqualTo("설명");
    }

    @Test
    void 설명만_전달하면_이름은_유지한다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);
        Project project = Project.create(creator, "WORK", "기존 이름", "기존 설명");

        // when
        project.update(null, "변경된 설명");

        // then
        assertThat(project.getName()).isEqualTo("기존 이름");
        assertThat(project.getDescription()).isEqualTo("변경된 설명");
    }

    @Test
    void 이름과_설명을_함께_변경한다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);
        Project project = Project.create(creator, "WORK", "기존 이름", "기존 설명");

        // when
        project.update("변경된 이름", "변경된 설명");

        // then
        assertThat(project.getName()).isEqualTo("변경된 이름");
        assertThat(project.getDescription()).isEqualTo("변경된 설명");
    }

    @Test
    void 이름과_설명이_모두_null이면_기존_값을_유지한다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);
        Project project = Project.create(creator, "WORK", "기존 이름", "기존 설명");

        // when
        project.update(null, null);

        // then
        assertThat(project.getName()).isEqualTo("기존 이름");
        assertThat(project.getDescription()).isEqualTo("기존 설명");
    }

    @Test
    void 빈_문자열_설명은_설명을_삭제한다() {
        // given
        User creator = User.create("생성자", "creator@example.com", null);
        Project project = Project.create(creator, "WORK", "기존 이름", "기존 설명");

        // when
        project.update(null, "");

        // then
        assertThat(project.getName()).isEqualTo("기존 이름");
        assertThat(project.getDescription()).isEmpty();
    }
}
