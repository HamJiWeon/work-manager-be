package work.managerbe.project.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import work.managerbe.project.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByCreator_IdAndCode(UUID creatorId, String code);

    /**
     * 목록 조회에 사용할 프로젝트를 코드로 조회한다.
     */
    Optional<Project> findByCode(String code);

    /**
     * 코드로 프로젝트를 조회하고 행을 잠가 같은 프로젝트의 보드 생성 순서 계산을 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.code = :code")
    Optional<Project> findByCodeForUpdate(@Param("code") String code);

    /**
     * 프로젝트 행을 잠가 같은 프로젝트의 보드 생성 순서 계산을 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :projectId")
    Optional<Project> findByIdForUpdate(@Param("projectId") Long projectId);

    /**
     * 탈퇴하지 않은 멤버의 프로젝트를 프로젝트 ID 순서로 조회한다.
     */
    @Query("""
        select m.project
        from Member m
        where m.user.id = :userId
          and m.leftAt is null
        order by m.project.id asc
        """)
    List<Project> findByUser_Id(@Param("userId") UUID userId);

    @Query("""
    select m.project
    from Member m
    where m.project.creator.id = :creatorId
      and m.project.code = :code
      and m.user.id = :requesterId
      and m.leftAt is null
    """)
    Optional<Project> findAccessibleProject(
            @Param("creatorId") UUID creatorId,
            @Param("code") String code,
            @Param("requesterId") UUID requesterId);
}
