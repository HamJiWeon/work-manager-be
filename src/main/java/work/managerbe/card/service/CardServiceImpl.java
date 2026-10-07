package work.managerbe.card.service;

import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import work.managerbe.board.domain.Board;
import work.managerbe.board.repository.BoardRepository;
import work.managerbe.card.domain.Card;
import work.managerbe.card.domain.CardStatus;
import work.managerbe.card.dto.request.CardCreateRequest;
import work.managerbe.card.dto.request.CardUpdateRequest;
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

    private static final int ORDER_INCREMENT = 1;
    private static final int ORDER_DECREMENT = -1;
    private static final int LAST_POSITION = Integer.MAX_VALUE;

    private final EntityManager entityManager;
    private final CardRepository cardRepository;
    private final ProjectRepository projectRepository;
    private final BoardRepository boardRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;

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

        card.move(board, request.status(), Math.toIntExact(cardRepository.countByBoard_IdAndStatus(boardId, request.status())));
        return CardResponse.from(cardRepository.saveAndFlush(card));
    }

    /**
     * 프로젝트 잠금으로 생성과 이동을 직렬화하고 양쪽 상태 목록을 재정렬한다. 생략된 필드는 유지한다.
     */
    @Override
    public CardResponse update(UUID creatorId, String code, Long boardId, Long cardId,
                               UUID requesterId, CardUpdateRequest request) {
        validateUpdateRequest(request, requesterId);
        Project project = findProjectForUpdate(creatorId, code, requesterId);
        Card card = findCard(project.getId(), boardId, cardId);
        Board targetBoard = resolveTargetBoard(card, request.getBoardId());
        CardStatus targetStatus = request.getStatus() == null ? card.getStatus() : request.getStatus();
        LocalDate startDate = request.isStartDateProvided() ? request.getStartDate() : card.getStartDate();
        LocalDate endDate = request.isEndDateProvided() ? request.getEndDate() : card.getEndDate();

        validateDates(startDate, endDate);
        boolean moved = moveCard(card, targetBoard, targetStatus, request.getSortOrder());
        card.update(request.getTitle(), request.getContent(), startDate, endDate);
        cardRepository.flush();
        CardResponse response = CardResponse.from(card);
        if (moved) {
            entityManager.clear();
        }
        return response;
    }

    /** 수정 요청의 필수값을 검증한 뒤 인증된 사용자의 존재를 확인한다. */
    private void validateUpdateRequest(CardUpdateRequest request, UUID requesterId) {

        if (request == null || !request.isValid()) {
            throw CardException.of(CardErrorCode.CARD_INVALID_UPDATE);
        }

        validateRequesterId(requesterId);
        if (!userRepository.existsById(requesterId)) {
            throw UserException.of(UserErrorCode.USER_NOT_FOUND);
        }
    }

    /** 프로젝트 잠금을 획득하고 요청자가 활성 멤버인지 확인한다. */
    private Project findProjectForUpdate(UUID creatorId, String code, UUID requesterId) {
        validateProjectIdentifier(creatorId, code);
        Project project = projectRepository.findByCreatorIdAndCodeForUpdate(creatorId, code)
                .orElseThrow(() -> ProjectException.of(ProjectErrorCode.PROJECT_NOT_FOUND));
        validateActiveMembership(requesterId, project.getId());
        return project;
    }

    /** 카드 ID와 경로의 프로젝트·보드 소속이 모두 일치하는 카드를 조회한다. */
    private Card findCard(Long projectId, Long boardId, Long cardId) {
        if (boardId == null || cardId == null) {
            throw CardException.of(CardErrorCode.CARD_NOT_FOUND);
        }
        return cardRepository.findByIdAndProject_IdAndBoard_Id(cardId, projectId, boardId)
                .orElseThrow(() -> CardException.of(CardErrorCode.CARD_NOT_FOUND));
    }

    /** 보드가 생략되면 현재 보드를 유지하고 이동 대상은 같은 프로젝트로 제한한다. */
    private Board resolveTargetBoard(Card card, Long requestedBoardId) {
        if (requestedBoardId == null || requestedBoardId.equals(card.getBoard().getId())) {
            return card.getBoard();
        }
        return boardRepository.findById(requestedBoardId)
                .filter(board -> board.getProject().getId().equals(card.getProject().getId()))
                .orElseThrow(() -> BoardException.of(BoardErrorCode.BOARD_NOT_FOUND));
    }

    /** 기존 일정과 요청 일정을 합친 결과에서 종료일이 시작일보다 앞서는지 검증한다. */
    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw CardException.of(CardErrorCode.CARD_INVALID_UPDATE);
        }
    }

    /** 대상 목록의 길이로 위치를 검증하고 영향받는 구간만 일괄 이동한다. 생략한 위치는 목록 끝이다. */
    private boolean moveCard(Card card, Board targetBoard, CardStatus targetStatus, Integer requestedPosition) {
        Long sourceBoardId = card.getBoard().getId();
        CardStatus sourceStatus = card.getStatus();
        int currentPosition = card.getSortOrder();
        boolean changedList = !targetBoard.getId().equals(sourceBoardId) || targetStatus != sourceStatus;
        if (!changedList && requestedPosition == null) {
            return false;
        }

        long targetSize = cardRepository.countByBoard_IdAndStatus(targetBoard.getId(), targetStatus);
        if (!changedList) {
            targetSize--;
        }
        int position = requestedPosition == null ? Math.toIntExact(targetSize) : requestedPosition;
        if (position > targetSize) {
            throw CardException.of(CardErrorCode.CARD_INVALID_UPDATE);
        }
        if (!changedList && position == currentPosition) {
            return false;
        }

        LocalDateTime updatedAt = LocalDateTime.now();
        if (changedList) {
            cardRepository.shiftOrder(sourceBoardId, sourceStatus, card.getId(),
                    currentPosition + ORDER_INCREMENT, LAST_POSITION, ORDER_DECREMENT, updatedAt);
            cardRepository.shiftOrder(targetBoard.getId(), targetStatus, card.getId(),
                    position, LAST_POSITION, ORDER_INCREMENT, updatedAt);
        } else if (position < currentPosition) {
            cardRepository.shiftOrder(sourceBoardId, sourceStatus, card.getId(),
                    position, currentPosition - ORDER_INCREMENT, ORDER_INCREMENT, updatedAt);
        } else {
            cardRepository.shiftOrder(sourceBoardId, sourceStatus, card.getId(),
                    currentPosition + ORDER_INCREMENT, position, ORDER_DECREMENT, updatedAt);
        }
        card.move(targetBoard, targetStatus, position);
        return true;
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
