package work.managerbe.board.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.board.domain.Board;
import work.managerbe.board.dto.BoardCreateRequest;
import work.managerbe.board.dto.BoardResponse;
import work.managerbe.board.repository.BoardRepository;
import work.managerbe.global.board.BoardErrorCode;
import work.managerbe.global.board.BoardException;
import work.managerbe.global.project.ProjectErrorCode;
import work.managerbe.global.project.ProjectException;
import work.managerbe.global.user.UserErrorCode;
import work.managerbe.global.user.UserException;
import work.managerbe.project.domain.Project;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class BoardServiceImpl implements BoardService {

    private final BoardRepository boardRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    /**
     * 이름과 사용자 존재를 검증하고 프로젝트 잠금 안에서 보드 목록 끝에 새 보드를 추가한다.
     */
    @Override
    public BoardResponse create(UUID userId, Long projectId, BoardCreateRequest request) {
        requestValidate(request);
        userValidate(userId);
        projectValidate(projectId);

        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));

        Board board = project.addBoard(request.name());
        return BoardResponse.from(boardRepository.save(board));
    }

    private static void projectValidate(Long projectId) {
        if (projectId == null) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
    }

    private void userValidate(UUID userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw UserException.of(UserErrorCode.USER_NOT_FOUND);
        }
    }

    private static void requestValidate(BoardCreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw BoardException.of(BoardErrorCode.BOARD_INVALID_NAME);
        }
    }

}
