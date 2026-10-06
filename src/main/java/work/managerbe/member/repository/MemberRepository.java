package work.managerbe.member.repository;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import work.managerbe.member.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 탈퇴 이력을 포함해 중복 참여를 검사한다. */
    boolean existsByUser_IdAndProject_Id(UUID userId, Long projectId);

    /** 프로젝트 내 활성 요청자를 조회한다. */
    Optional<Member> findByUser_IdAndProject_IdAndLeftAtIsNull(UUID userId, Long projectId);

    /** 다른 프로젝트와 탈퇴한 멤버를 제외한 단건을 조회한다. */
    Optional<Member> findByIdAndProject_IdAndLeftAtIsNull(Long id, Long projectId);

    /** 활성 멤버를 가입 시각과 ID 순으로 페이지 조회한다. */
    Page<Member> findAllByProject_IdAndLeftAtIsNull(
            Long projectId, Pageable pageable);


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
