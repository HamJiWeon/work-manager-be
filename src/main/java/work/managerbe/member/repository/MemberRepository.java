package work.managerbe.member.repository;

import work.managerbe.member.domain.MemberRole;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import work.managerbe.member.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /** 프로젝트의 활성 참여자를 가입 시각과 ID 오름차순으로 페이지 조회하고 전체 개수를 계산한다. */
    Page<Member> findAllByProject_IdAndLeftAtIsNullOrderByJoinedAtAscIdAsc(Long projectId, Pageable pageable);

    /** 멤버 ID와 프로젝트 소속을 함께 확인하고 탈퇴 이력도 조회한다. */
    Optional<Member> findByIdAndProject_Id(Long memberId, Long projectId);

    /** 프로젝트에 속하고 탈퇴하지 않은 멤버만 일반 조회·역할 변경 대상으로 찾는다. */
    Optional<Member> findByIdAndProject_IdAndLeftAtIsNull(Long memberId, Long projectId);

    /** 요청자가 해당 프로젝트에 활성 상태로 가입했고 허용된 역할인지 확인한다. */
    boolean existsByUser_IdAndProject_IdAndLeftAtIsNullAndRoleIn(
            UUID userId, Long projectId, Collection<MemberRole> roles);

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
