package work.managerbe.card.service;

import java.util.UUID;
import work.managerbe.card.dto.request.CardFilterRequest;
import work.managerbe.card.dto.request.CardCreateRequest;
import work.managerbe.card.dto.request.CardUpdateRequest;
import work.managerbe.card.dto.response.CardResponse;
import work.managerbe.card.dto.response.CardSliceResponse;

public interface CardService {
    /**
     * 필터 없이 보드의 카드 목록을 조회한다.
     */
    default CardSliceResponse getAll(UUID creatorId, String code, Long boardId, UUID requesterId, int page, int size) {
        return getAll(creatorId, code, boardId, requesterId, page, size, CardFilterRequest.empty());
    }

    /**
     * 활성 멤버에게 해당 보드의 카드 목록과 다음 데이터 존재 여부를 반환한다.
     */
    CardSliceResponse getAll(UUID creatorId, String code, Long boardId, UUID requesterId, int page, int size, CardFilterRequest filter);

    /**
     * 프로젝트의 활성 멤버에게 해당 프로젝트와 보드에 속한 카드 정보를 반환한다.
     */
    CardResponse get(UUID creatorId, String code, Long boardId, Long cardId, UUID requesterId);

    /**
     * 프로젝트의 활성 멤버가 해당 보드에 카드를 생성한다.
     */
    CardResponse create(UUID creatorId, String code, Long boardId, UUID requesterId, CardCreateRequest request);

    /**
     * 활성 멤버가 카드 내용과 일정, 보드·상태·순서를 같은 트랜잭션에서 수정한다.
     */
    CardResponse update(UUID creatorId, String code, Long boardId, Long cardId, UUID requesterId, CardUpdateRequest request);

}
