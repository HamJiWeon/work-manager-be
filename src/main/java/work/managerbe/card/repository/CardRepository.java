package work.managerbe.card.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import work.managerbe.card.domain.Card;

public interface CardRepository extends JpaRepository<Card, Long> {
}
