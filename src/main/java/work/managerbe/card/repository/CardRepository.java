package work.managerbe.card.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import work.managerbe.card.domain.Card;

public interface CardRepository extends JpaRepository<Card, Long> {
    /**
     * 보드에 속한 카드를 보드 삭제 전에 제거한다.
     */
    void deleteAllByBoard_Id(Long boardId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Card c where c.project.id = :projectId")
    int deleteAllByProjectId(@Param("projectId") Long projectId);
}
