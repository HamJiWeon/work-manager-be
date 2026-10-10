package work.managerbe.card.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import work.managerbe.card.domain.CardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import work.managerbe.card.domain.Card;

public interface CardRepository extends JpaRepository<Card, Long>, CardQueryRepository {
    /**
     * ID와 프로젝트 및 보드 소속이 모두 일치하는 카드만 조회한다.
     */
    Optional<Card> findByIdAndProject_IdAndBoard_Id(Long id, Long projectId, Long boardId);

    /**
     * 보드에 속한 카드를 보드 삭제 전에 제거한다.
     */
    void deleteAllByBoard_Id(Long boardId);

    /**
     * 보드와 상태별로 저장된 위치와 ID 순서로 카드를 조회한다. 수정 시 프로젝트 잠금을 먼저 획득한다.
     */
    @Query("""
            select c from Card c
            where c.board.id = :boardId and c.status = :status
            order by c.sortOrder asc, c.id asc
            """)
    List<Card> findAllByBoardIdAndStatus(
            @Param("boardId") Long boardId, @Param("status") CardStatus status);

    /**
     * 새 카드를 해당 상태 목록 끝에 추가하기 위한 현재 개수를 반환한다.
     */
    long countByBoard_IdAndStatus(Long boardId, CardStatus status);

    /**
     * 지정 구간의 다른 카드 위치와 감사 시각을 일괄 갱신한다. 호출자는 완료 후 영속성 컨텍스트를 비운다.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Card c
            set c.sortOrder = c.sortOrder + :offset, c.updatedAt = :updatedAt
            where c.board.id = :boardId and c.status = :status
              and c.id <> :excludedCardId
              and c.sortOrder between :startPosition and :endPosition
            """)
    int shiftOrder(@Param("boardId") Long boardId, @Param("status") CardStatus status,
                   @Param("excludedCardId") Long excludedCardId,
                   @Param("startPosition") int startPosition, @Param("endPosition") int endPosition,
                   @Param("offset") int offset, @Param("updatedAt") LocalDateTime updatedAt);

}
