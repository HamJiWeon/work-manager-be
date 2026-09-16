package work.managerbe.board.service;

import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.board.domain.Board;
import work.managerbe.board.dto.BoardCreateRequest;
import work.managerbe.board.dto.BoardResponse;
import work.managerbe.board.dto.BoardPageResponse;
import work.managerbe.board.repository.BoardRepository;
import work.managerbe.global.exception.board.BoardErrorCode;
import work.managerbe.global.exception.board.BoardException;
import work.managerbe.global.exception.project.ProjectErrorCode;
import work.managerbe.global.exception.project.ProjectException;
import work.managerbe.global.exception.user.UserErrorCode;
import work.managerbe.global.exception.user.UserException;
import work.managerbe.project.domain.Project;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.user.repository.UserRepository;
import work.managerbe.member.repository.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class BoardServiceImpl implements BoardService {

    private final BoardRepository boardRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;

    /**
     * 이름과 사용자를 검증하고 코드로 조회한 프로젝트를 잠근 뒤 활성 멤버의 보드를 추가한다.
     */
    @Override
    public BoardResponse create(UUID userId, String code, BoardCreateRequest request) {
        requestValidate(request);
        userValidate(userId);
        projectValidate(code);

        Project project = projectRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!memberRepository.existsByUser_IdAndProject_IdAndLeftAtIsNull(userId, project.getId())) {
            throw new AccessDeniedException("프로젝트의 활성 멤버만 보드를 생성할 수 있습니다.");
        }

        Board board = project.addBoard(request.name());
        return BoardResponse.from(boardRepository.save(board));
    }

    /**
     * 사용자와 프로젝트의 활성 멤버 여부를 확인한 뒤 잠금 없이 보드 페이지를 조회한다.
     */
    @Override
    @Transactional(readOnly = true)
    public BoardPageResponse getAll(UUID userId, String code, int page, int size) {
        userValidate(userId);
        projectValidate(code);

        Project project = projectRepository.findByCode(code)
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!memberRepository.existsByUser_IdAndProject_IdAndLeftAtIsNull(userId, project.getId())) {
            throw new AccessDeniedException("프로젝트의 활성 멤버만 보드를 조회할 수 있습니다.");
        }

        return BoardPageResponse.from(boardRepository.findByProject_IdOrderBySortOrderAscIdAsc(
                project.getId(), PageRequest.of(page, size)));
    }

    private static void projectValidate(String code) {
        if (code == null || code.isBlank()) {
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
