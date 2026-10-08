package work.managerbe.workspace.domain;

import work.managerbe.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import work.managerbe.project.domain.Project;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceTest {

    @Test
    @DisplayName("워크스페이스를 생성하면 프로젝트와 입력 내용이 저장된다.")
    void 워크스페이스_생성() {
        // given
        Project project = Project.create(User.create("생성자", "creator@example.com", null), "WORK_test", "업무 관리", "프로젝트 설명");

        // when
        Workspace workspace = Workspace.create(project, "회의록", "회의 내용");

        // then
        assertThat(workspace.getProject()).isSameAs(project);
        assertThat(workspace.getTitle()).isEqualTo("회의록");
        assertThat(workspace.getContent()).isEqualTo("회의 내용");
    }

    /** null인 필드는 유지하고 빈 문자열을 포함한 전달값만 변경하는 부분 수정 규칙을 검증한다. */
    @ParameterizedTest
    @CsvSource(value = {
            "새 제목,새 내용,새 제목,새 내용",
            "새 제목,NULL,새 제목,기존 내용",
            "NULL,새 내용,기존 제목,새 내용",
            "NULL,NULL,기존 제목,기존 내용",
            "'','', '', ''"
    }, nullValues = "NULL")
    void 전달한_필드만_수정하고_null인_필드는_유지한다(
            String title, String content, String expectedTitle, String expectedContent) {
        // given
        Workspace workspace = Workspace.create(null, "기존 제목", "기존 내용");

        // when
        workspace.update(title, content);

        // then
        assertThat(workspace.getTitle()).isEqualTo(expectedTitle);
        assertThat(workspace.getContent()).isEqualTo(expectedContent);
    }

    /** 기존 값이 null이어도 새 값을 지정할 수 있는지 검증한다. */
    @Test
    void 기존_null_필드에_새_값을_지정한다() {
        // given
        Workspace workspace = Workspace.create(null, null, null);

        // when
        workspace.update("새 제목", "새 내용");

        // then
        assertThat(workspace.getTitle()).isEqualTo("새 제목");
        assertThat(workspace.getContent()).isEqualTo("새 내용");
    }
}
