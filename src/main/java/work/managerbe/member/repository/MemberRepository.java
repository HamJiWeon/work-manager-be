package work.managerbe.member.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import work.managerbe.member.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
    /**
     * 사용자와 프로젝트가 일치하고 탈퇴 시각이 없는 활성 참여 여부를 조회한다.
     */
    boolean existsByUser_IdAndProject_IdAndLeftAtIsNull(UUID userId, Long projectId);
}
