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

    /** 가입 검증 중 사용한 약관 버전이 변경되지 않도록 활성 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RegistrationTerm t where t.active = true order by t.code")
    List<RegistrationTerm> lockActiveTerms();
}
