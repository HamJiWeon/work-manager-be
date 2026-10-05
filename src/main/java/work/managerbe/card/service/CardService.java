package work.managerbe.card.service;

import java.util.UUID;
import work.managerbe.card.dto.request.CardCreateRequest;
import work.managerbe.card.dto.response.CardResponse;

public interface CardService {
    /**
     * 프로젝트의 활성 멤버가 해당 보드에 카드를 생성한다.
     */
    CardResponse create(UUID creatorId, String code, Long boardId, UUID requesterId, CardCreateRequest request);
}
