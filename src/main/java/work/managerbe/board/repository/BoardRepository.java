package work.managerbe.board.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import work.managerbe.board.domain.Board;

public interface BoardRepository extends JpaRepository<Board, Long> {
    /**
     * 프로젝트의 마지막 정렬 순서를 조회하고 보드가 없으면 0을 반환한다.
     */
    @Query("select coalesce(max(b.sortOrder), 0) from Board b where b.project.id = :projectId")
    int findMaxSortOrderByProjectId(@Param("projectId") Long projectId);
}
