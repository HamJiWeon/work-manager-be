package work.managerbe.workspace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import work.managerbe.workspace.domain.Workspace;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
}
