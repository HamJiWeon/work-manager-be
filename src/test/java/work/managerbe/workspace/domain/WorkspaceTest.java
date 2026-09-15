package work.managerbe.workspace.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import work.managerbe.project.domain.Project;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceTest {

    @Test
    @DisplayName("워크스페이스를 생성하면 프로젝트와 입력 내용이 저장된다.")
    void 워크스페이스_생성() {
        // given
        Project project = Project.create("WORK_test", "업무 관리", "프로젝트 설명");

        // when
        Workspace workspace = Workspace.create(project, "회의록", "회의 내용");

        // then
        assertThat(workspace.getProject()).isSameAs(project);
        assertThat(workspace.getTitle()).isEqualTo("회의록");
        assertThat(workspace.getContent()).isEqualTo("회의 내용");
    }
}