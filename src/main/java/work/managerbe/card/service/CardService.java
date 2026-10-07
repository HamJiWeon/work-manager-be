package work.managerbe.card.service;

import java.util.UUID;
import work.managerbe.card.dto.request.CardCreateRequest;
import work.managerbe.card.dto.request.CardUpdateRequest;
import work.managerbe.card.dto.response.CardResponse;

public interface CardService {
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
