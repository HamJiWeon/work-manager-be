package work.managerbe.card.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.board.domain.Board;
import work.managerbe.board.repository.BoardRepository;
import work.managerbe.card.domain.Card;
import work.managerbe.card.dto.request.CardCreateRequest;
import work.managerbe.card.dto.response.CardResponse;
import work.managerbe.card.repository.CardRepository;
import work.managerbe.global.exception.board.*;
import work.managerbe.global.exception.card.*;
import work.managerbe.global.exception.member.*;
import work.managerbe.global.exception.project.*;
import work.managerbe.global.exception.user.*;
import work.managerbe.member.domain.Member;
import work.managerbe.member.repository.MemberRepository;
import work.managerbe.project.domain.Project;
import work.managerbe.project.repository.ProjectRepository;
import work.managerbe.user.domain.User;
import work.managerbe.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class CardServiceImpl implements CardService {

    private final CardRepository cardRepository;
    private final ProjectRepository projectRepository;
    private final BoardRepository boardRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;

    /**
     * 읽기 전용 트랜잭션에서 사용자와 프로젝트의 활성 참여를 확인한다.
     * 카드 ID와 프로젝트·보드 소속이 모두 일치할 때만 응답하며 불일치는 카드 없음으로 처리한다.
     */
    @Override
    @Transactional(readOnly = true)
    public CardResponse get(UUID creatorId, String code, Long boardId, Long cardId, UUID requesterId) {
        validateRequesterId(requesterId);

        if (!userRepository.existsById(requesterId)) {
            throw UserException.of(UserErrorCode.USER_NOT_FOUND);
        }

        validateProjectIdentifier(creatorId, code);

        Project project = projectRepository.findByCreator_IdAndCode(creatorId, code)
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!memberRepository.existsByUserIdAndProjectId(requesterId, project.getId())) {
            throw new AccessDeniedException("프로젝트의 활성 멤버만 카드를 조회할 수 있습니다.");
        }

        if (cardId == null || boardId == null) {
            throw CardException.of(CardErrorCode.CARD_NOT_FOUND);
        }

        Card card = cardRepository.findByIdAndProject_IdAndBoard_Id(cardId, project.getId(), boardId)
                .orElseThrow(() -> CardException.of(CardErrorCode.CARD_NOT_FOUND));
        return CardResponse.from(card);
    }

    /**
     * 프로젝트 잠금으로 보드 삭제와 생성을 직렬화하고 요청자의 활성 참여를 확인한다.
     * 경로·본문 ID 및 보드·활성 담당자의 프로젝트 소속을 검증한 뒤 인증된 사용자를 생성자로 저장한다.
     */
    @Override
    public CardResponse create(UUID creatorId, String code, Long boardId, UUID requesterId,
                               CardCreateRequest request) {
        validateCreateRequest(request, boardId);
        validateRequesterId(requesterId);

        User user = userRepository.findById(requesterId)
                .orElseThrow(() -> UserException.of(UserErrorCode.USER_NOT_FOUND));

        validateProjectIdentifier(creatorId, code);

        Project project = projectRepository.findByCreatorIdAndCodeForUpdate(creatorId, code)
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));

        validateActiveMembership(requesterId, project.getId());
        validateProjectIdMatches(request.projectId(), project.getId());

        Board board = boardRepository.findById(boardId)
                .filter(candidate -> project.getId().equals(candidate.getProject().getId()))
                .orElseThrow(() -> BoardException.of(BoardErrorCode.BOARD_NOT_FOUND));

        Member member = memberRepository.findById(request.memberId())
                .filter(candidate -> project.getId().equals(candidate.getProject().getId())
                        && candidate.getLeftAt() == null)
                .orElseThrow(() -> MemberException.of(MemberErrorCode.MEMBER_NOT_FOUND));

        Card card = Card.create(user, request.username(), request.title(), request.content(), request.status(),
                member, project, board, request.startDate(), request.endDate());

        return CardResponse.from(cardRepository.saveAndFlush(card));
    }

    /**
     * 생성 요청의 필수값과 일정, 경로와 본문의 보드 ID 일치 여부를 확인한다.
     */
    private static void validateCreateRequest(CardCreateRequest request, Long boardId) {
        if (request == null || !request.isValid() || !request.boardId().equals(boardId)) {
            throw CardException.of(CardErrorCode.CARD_INVALID_REQUEST);
        }
    }

    /**
     * 인증된 요청자의 ID가 없으면 사용자 오류를 반환한다.
     */
    private static void validateRequesterId(UUID requesterId) {
        if (requesterId == null) {
            throw UserException.of(UserErrorCode.USER_NOT_FOUND);
        }
    }

    /**
     * 프로젝트를 식별할 생성자 ID와 코드가 존재하는지 확인한다.
     */
    private static void validateProjectIdentifier(UUID creatorId, String code) {
        if (creatorId == null || code == null || code.isBlank()) {
            throw ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
    }

    /**
     * 본문의 프로젝트 ID가 경로로 조회한 프로젝트 ID와 일치하는지 확인한다.
     */
    private static void validateProjectIdMatches(Long requestedProjectId, Long projectId) {
        if (!requestedProjectId.equals(projectId)) {
            throw CardException.of(CardErrorCode.CARD_INVALID_REQUEST);
        }
    }

    /**
     * 요청자가 프로젝트의 활성 멤버인지 확인한다.
     */
    private void validateActiveMembership(UUID requesterId, Long projectId) {
        if (!memberRepository.existsByUserIdAndProjectId(requesterId, projectId)) {
            throw new AccessDeniedException("프로젝트의 활성 멤버만 카드를 생성할 수 있습니다.");
        }
    }
}
