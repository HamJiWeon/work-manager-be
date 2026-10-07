package work.managerbe.card.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import work.managerbe.card.domain.Card;

public interface CardRepository extends JpaRepository<Card, Long> {
    /**
     * ID와 프로젝트 및 보드 소속이 모두 일치하는 카드만 조회한다.
     */
    Optional<Card> findByIdAndProject_IdAndBoard_Id(Long id, Long projectId, Long boardId);

    /**
     * 보드에 속한 카드를 보드 삭제 전에 제거한다.
     */
    void deleteAllByBoard_Id(Long boardId);
}
