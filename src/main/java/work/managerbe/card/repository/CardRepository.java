package work.managerbe.card.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import work.managerbe.card.domain.Card;

public interface CardRepository extends JpaRepository<Card, Long> {
    /**
     * 보드에 속한 카드를 보드 삭제 전에 제거한다.
     */
    void deleteAllByBoard_Id(Long boardId);
}
