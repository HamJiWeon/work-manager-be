package work.managerbe.board.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import work.managerbe.board.domain.Board;

public interface BoardRepository extends JpaRepository<Board, Long> {
}
