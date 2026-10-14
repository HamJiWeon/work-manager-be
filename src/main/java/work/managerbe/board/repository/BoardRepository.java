package work.managerbe.board.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import work.managerbe.board.domain.Board;
import work.managerbe.card.domain.CardStatus;

public interface BoardRepository extends JpaRepository<Board, Long> {

    /**
     * 전체 개수 조회 없이 대상 프로젝트의 보드를 정렬 순서와 ID 오름차순으로 슬라이스 조회한다.
     */
    @Query("select b from Board b where b.project.id = :projectId order by b.sortOrder asc, b.id asc")
    Slice<Board> findAllByProjectId(@Param("projectId") Long projectId, Pageable pageable);

    /**
     * 카드가 존재하고 모두 완료된 보드를 완료 목록으로 분류하고,
     * 나머지 보드는 미완료 목록으로 분류하여 기존 순서대로 페이징한다.
     */
    @Query("""
        select b from Board b
        where b.project.id = :projectId
          and (
            (
              :completed = true
              and exists (
                select c.id from Card c
                where c.board.id = b.id
              )
              and not exists (
                select c.id from Card c
                where c.board.id = b.id
                  and c.status <> :doneStatus
              )
            )
            or
            (
              :completed = false
              and (
                not exists (
                  select c.id from Card c
                  where c.board.id = b.id
                )
                or exists (
                  select c.id from Card c
                  where c.board.id = b.id
                    and c.status <> :doneStatus
                )
              )
            )
          )
        order by b.sortOrder asc, b.id asc
        """)
    Slice<Board> findByProjectIdAndCompleted(
            @Param("projectId") Long projectId,
            @Param("completed") boolean completed,
            @Param("doneStatus") CardStatus doneStatus,
            Pageable pageable);
}
