package work.managerbe.board.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Pageable;
import work.managerbe.board.domain.Board;

public interface BoardRepository extends JpaRepository<Board, Long> {

    /**
     * 전체 개수 조회 없이 대상 프로젝트의 보드를 정렬 순서와 ID 오름차순으로 슬라이스 조회한다.
     */
    @Query("select b from Board b where b.project.id = :projectId order by b.sortOrder asc, b.id asc")
    Slice<Board> findAllByProjectId(@Param("projectId") Long projectId, Pageable pageable);
}
