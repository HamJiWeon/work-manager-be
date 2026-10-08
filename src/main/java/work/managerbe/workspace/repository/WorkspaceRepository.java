package work.managerbe.workspace.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import work.managerbe.workspace.domain.Workspace;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long>, WorkspaceRepositoryCustom {

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

    /**
     * 프로젝트 경로에 속한 워크스페이스를 잠가 동시 수정을 순차 처리한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select w
        from Workspace w
        where w.id = :workspaceId
            and w.project.creator.id = :creatorId
            and w.project.code = :code
    """)
    Optional<Workspace> findByProjectPathForUpdate(
            @Param("workspaceId") Long workspaceId,
            @Param("creatorId") UUID creatorId,
            @Param("code") String code
    );
}
