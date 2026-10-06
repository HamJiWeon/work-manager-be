package work.managerbe.workspace.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import work.managerbe.workspace.domain.Workspace;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    @Query("""
        select w
        from Workspace w
        where w.id = :workspaceId
          and w.project.creator.id = :creatorId
          and w.project.code = :code
        """)
    Optional<Workspace> findByProjectPath(
            @Param("workspaceId") Long workspaceId,
            @Param("creatorId") UUID creatorId,
            @Param("code") String code
    );

}
