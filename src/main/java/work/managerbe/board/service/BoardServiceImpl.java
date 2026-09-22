package work.managerbe.board.service;

import java.util.UUID;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.board.domain.Board;
import work.managerbe.board.dto.BoardCreateRequest;
import work.managerbe.board.dto.BoardResponse;
import work.managerbe.board.dto.BoardUpdateRequest;
import work.managerbe.board.dto.BoardUpdateItem;
import work.managerbe.board.dto.BoardSliceResponse;
import work.managerbe.board.repository.BoardRepository;
import work.managerbe.card.repository.CardRepository;
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

/**
 * 생성자 ID와 코드로 프로젝트를 식별하고 활성 멤버의 보드 생성, 조회 및 수정을 처리한다.
 * 생성과 수정은 프로젝트 행 잠금으로 직렬화하며, 목록은 읽기 전용 트랜잭션으로 조회한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BoardServiceImpl implements BoardService {

    private final BoardRepository boardRepository;
    private final CardRepository cardRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;

    /**
     * 요청과 사용자 존재 여부를 검증하고 프로젝트 행을 잠근 뒤 목록 끝에 보드를 추가한다.
     * 요청자가 프로젝트의 활성 멤버인 경우에만 생성을 허용한다.
     *
     * @param creatorId 프로젝트 생성자 ID
     * @param code 생성자 범위에서 프로젝트를 식별하는 코드
     * @param requesterId 보드 생성을 요청한 사용자 ID
     * @param request 생성할 보드 이름
     * @return 생성된 보드 정보
     * @throws BoardException 이름이 유효하지 않거나 보드 정렬 순서 한도에 도달한 경우
     * @throws UserException 요청자 ID가 없거나 사용자가 존재하지 않는 경우
     * @throws ProjectException 생성자 ID나 코드가 없거나 프로젝트를 찾을 수 없는 경우
     * @throws AccessDeniedException 요청자가 프로젝트의 활성 멤버가 아닌 경우
     */
    @Override
    public BoardResponse create(UUID creatorId, String code, UUID requesterId, BoardCreateRequest request) {
        validateCreateRequest(request);
        validateUserExists(requesterId);
        validateCreatorId(creatorId);
        validateProjectCode(code);

        Project project = findProjectForUpdate(creatorId, code);

        if (!memberRepository.existsByUserIdAndProjectId(requesterId, project.getId())) {
            throw new AccessDeniedException("프로젝트의 활성 멤버만 보드를 생성할 수 있습니다.");
        }

        Board board = project.addBoard(request.name());
        return BoardResponse.from(boardRepository.save(board));
    }

    /**
     * 요청자의 활성 멤버 여부를 확인하고 보드를 정렬 순서와 ID 오름차순으로 조회한다.
     * 전체 개수를 조회하지 않는 슬라이스를 사용해 다음 데이터 존재 여부를 반환한다.
     *
     * @param creatorId 프로젝트 생성자 ID
     * @param code 생성자 범위에서 프로젝트를 식별하는 코드
     * @param requesterId 목록 조회를 요청한 사용자 ID
     * @param page 0부터 시작하는 페이지 번호
     * @param size 조회할 보드 수. API의 최대 크기는 컨트롤러에서 검증한다.
     * @return 보드 목록과 페이지 정보 및 다음 데이터 존재 여부
     * @throws UserException 요청자 ID가 없거나 사용자가 존재하지 않는 경우
     * @throws ProjectException 생성자 ID나 코드가 없거나 프로젝트를 찾을 수 없는 경우
     * @throws AccessDeniedException 요청자가 프로젝트의 활성 멤버가 아닌 경우
     * @throws IllegalArgumentException 페이지 번호가 음수이거나 조회 크기가 1 미만인 경우
     */
    @Override
    @Transactional(readOnly = true)
    public BoardSliceResponse getAll(UUID creatorId, String code, UUID requesterId, int page, int size) {
        validateUserExists(requesterId);
        validateCreatorId(creatorId);
        validateProjectCode(code);

        Project project = projectRepository.findByCreator_IdAndCode(creatorId, code)
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!memberRepository.existsByUserIdAndProjectId(requesterId, project.getId())) {
            throw new AccessDeniedException("프로젝트의 활성 멤버만 보드를 조회할 수 있습니다.");
        }

        return BoardSliceResponse.from(boardRepository.findAllByProjectId(
                project.getId(), PageRequest.of(page, size)));
    }

    /**
     * 프로젝트 행을 잠그고 요청 배열을 최종 순서로 적용하며 전달된 이름을 같은 트랜잭션에서 수정한다.
     * 요청 ID 집합이 현재 프로젝트 보드 전체와 다르면 오래된 목록으로 판단해 충돌을 반환한다.
     *
     * @param creatorId 프로젝트 생성자 ID
     * @param code 생성자 범위에서 프로젝트를 식별하는 코드
     * @param requesterId 보드 수정을 요청한 사용자 ID
     * @param request 최종 순서의 전체 보드와 선택적인 새 이름
     * @return 최종 순서로 정렬된 보드 정보
     * @throws BoardException 요청 형식이나 이름이 유효하지 않거나 현재 보드 목록과 충돌하는 경우
     * @throws UserException 요청자 ID가 없거나 사용자가 존재하지 않는 경우
     * @throws ProjectException 생성자 ID나 코드가 없거나 프로젝트를 찾을 수 없는 경우
     * @throws AccessDeniedException 요청자가 프로젝트의 활성 멤버가 아닌 경우
     */
    @Override
    public List<BoardResponse> update(UUID creatorId, String code, UUID requesterId, BoardUpdateRequest request) {
        validateUpdateRequest(request);
        validateUserExists(requesterId);
        validateCreatorId(creatorId);
        validateProjectCode(code);
        
        Project project = findProjectForUpdate(creatorId, code);

        if (!memberRepository.existsByUserIdAndProjectId(requesterId, project.getId())) {
            throw new AccessDeniedException("프로젝트의 활성 멤버만 보드를 수정할 수 있습니다.");
        }

        List<Long> boardIds = request.boards().stream()
                .map(BoardUpdateItem::boardId)
                .toList();

        project.reorderBoards(boardIds);

        for (int index = 0; index < request.boards().size(); index++) {
            String name = request.boards().get(index).name();
            if (name != null) {
                project.getBoards().get(index).rename(name);
            }
        }

        boardRepository.flush();

        return project.getBoards().stream()
                .map(BoardResponse::from)
                .toList();
    }

    /**
     * 프로젝트 행을 잠그고 활성 멤버를 확인한 뒤 카드, 보드 순으로 삭제한다.
     * 보드가 다른 프로젝트에 속하거나 존재하지 않으면 같은 오류를 반환한다.
     */
    @Override
    public void delete(UUID creatorId, String code, UUID requesterId, Long boardId) {
        validateUserExists(requesterId);
        validateCreatorId(creatorId);
        validateProjectCode(code);

        Project project = findProjectForUpdate(creatorId, code);

        if (!memberRepository.existsByUserIdAndProjectId(requesterId, project.getId())) {
            throw new AccessDeniedException("프로젝트의 활성 멤버만 보드를 삭제할 수 있습니다.");
        }

        if (boardId == null) {
            throw BoardException.of(BoardErrorCode.BOARD_NOT_FOUND);
        }

        Board board = project.getBoards().stream()
                .filter(candidate -> boardId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> BoardException.of(BoardErrorCode.BOARD_NOT_FOUND));

        cardRepository.deleteAllByBoard_Id(boardId);
        project.removeBoard(board);
        boardRepository.delete(board);
    }

    /**
     * 수정 요청에 전체 보드 배열과 중복 없는 ID가 있고 전달된 이름이 공백이 아닌지 확인한다.
     * 개별 필드 값의 유효성은 후속 검증에서 확인한다.
     *
     * @param request 수정 요청
     * @throws BoardException 요청 형식이나 이름이 유효하지 않은 경우
     */
    private static void validateUpdateRequest(BoardUpdateRequest request) {
        if (request == null || request.boards() == null || request.boards().isEmpty()) {
            throw BoardException.of(BoardErrorCode.BOARD_INVALID_UPDATE);
        }

        Set<Long> boardIds = new HashSet<>();
        for (BoardUpdateItem item : request.boards()) {
            if (item == null || item.boardId() == null || !boardIds.add(item.boardId())) {
                throw BoardException.of(BoardErrorCode.BOARD_INVALID_UPDATE);
            }
            if (item.name() != null && item.name().isBlank()) {
                throw BoardException.of(BoardErrorCode.BOARD_INVALID_NAME);
            }
        }
    }

    /**
     * 생성자 ID와 코드로 프로젝트 행을 잠가 조회하고 없으면 프로젝트 오류를 반환한다.
     *
     * @param creatorId 프로젝트 생성자 ID
     * @param code 프로젝트 코드
     * @return 잠금이 적용된 프로젝트
     * @throws ProjectException 프로젝트를 찾을 수 없는 경우
     */
    private Project findProjectForUpdate(UUID creatorId, String code) {
        return projectRepository.findByCreatorIdAndCodeForUpdate(creatorId, code)
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    /**
     * 프로젝트 식별에 필요한 생성자 ID의 null 여부를 확인한다.
     * 생성자의 실제 존재 여부를 별도로 조회하지 않는다.
     *
     * @param creatorId 프로젝트 생성자 ID
     * @throws ProjectException 생성자 ID가 null인 경우
     */
    private static void validateCreatorId(UUID creatorId) {
        if (creatorId == null) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
    }

    /**
     * 프로젝트 코드가 null, 빈 문자열 또는 공백인지 확인한다.
     * 프로젝트 존재 여부는 이후 저장소 조회에서 확인한다.
     *
     * @param code 프로젝트 코드
     * @throws ProjectException 코드가 없거나 공백인 경우
     */
    private static void validateProjectCode(String code) {
        if (code == null || code.isBlank()) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
    }

    /**
     * 사용자 ID가 있는지 확인하고 저장소에서 실제 사용자 존재 여부를 검증한다.
     *
     * @param userId 검증할 사용자 ID
     * @throws UserException ID가 null이거나 사용자가 존재하지 않는 경우
     */
    private void validateUserExists(UUID userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw UserException.of(UserErrorCode.USER_NOT_FOUND);
        }
    }

    /**
     * 생성 요청과 필수 이름이 존재하는지 확인하고 빈 문자열 또는 공백 이름을 거절한다.
     *
     * @param request 보드 생성 요청
     * @throws BoardException 요청이나 이름이 null이거나 이름이 빈 문자열 또는 공백인 경우
     */
    private static void validateCreateRequest(BoardCreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw BoardException.of(BoardErrorCode.BOARD_INVALID_NAME);
        }
    }

}
