package work.managerbe.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import work.managerbe.member.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
