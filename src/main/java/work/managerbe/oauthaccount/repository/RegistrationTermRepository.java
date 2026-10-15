package work.managerbe.oauthaccount.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import work.managerbe.oauthaccount.domain.RegistrationTerm;

import java.util.List;
import java.util.UUID;

public interface RegistrationTermRepository extends JpaRepository<RegistrationTerm, UUID> {
    List<RegistrationTerm> findByActiveTrueOrderByCodeAsc();

    /** 활성 행을 잠그며 가입 트랜잭션의 REPEATABLE_READ와 함께 개정 충돌을 감지한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RegistrationTerm t where t.active = true order by t.code")
    List<RegistrationTerm> lockActiveTerms();
}
