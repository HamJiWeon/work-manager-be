package work.managerbe.board.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import work.managerbe.board.domain.Board;

public interface BoardRepository extends JpaRepository<Board, Long> {

    /**
     * 대상 프로젝트의 보드를 정렬 순서와 ID 오름차순으로 페이지 조회한다.
     */
    Page<Board> findByProject_IdOrderBySortOrderAscIdAsc(Long projectId, Pageable pageable);
}
