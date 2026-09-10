package work.managerbe.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import work.managerbe.user.domain.User;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
}
