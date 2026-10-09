package work.managerbe.member.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import work.managerbe.member.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 탈퇴 여부와 관계없이 프로젝트의 기존 가입 이력을 확인한다. */
    boolean existsByUser_IdAndProject_Id(UUID userId, Long projectId);

    /**
     * 사용자와 프로젝트가 일치하고 탈퇴 시각이 없는 활성 참여 여부를 조회한다.
     */
    @Query("""
            select case when count(m) > 0 then true else false end
            from Member m
            where m.user.id = :userId
              and m.project.id = :projectId
              and m.leftAt is null
            """)
    boolean existsByUserIdAndProjectId(
            @Param("userId") UUID userId, @Param("projectId") Long projectId);
}
