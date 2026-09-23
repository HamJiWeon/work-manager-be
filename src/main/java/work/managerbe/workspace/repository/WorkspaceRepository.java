package work.managerbe.workspace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import work.managerbe.workspace.domain.Workspace;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Workspace w where w.project.id = :projectId")
    int deleteAllByProjectId(@Param("projectId") Long projectId);
}
