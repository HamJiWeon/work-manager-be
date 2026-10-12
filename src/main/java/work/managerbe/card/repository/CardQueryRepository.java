package work.managerbe.card.repository;

import org.springframework.data.domain.Pageable;
import work.managerbe.card.dto.request.CardFilterRequest;
import org.springframework.data.domain.Slice;
import work.managerbe.card.domain.Card;

/**
 * 카드 목록의 선택적 필터와 무한 스크롤 조회를 정의한다.
 */
public interface CardQueryRepository {
    /**
     * 프로젝트와 보드 범위에서 선택적 필터를 적용한 슬라이스를 반환한다.
     */
    Slice<Card> findSlice(Long projectId, Long boardId, CardFilterRequest filter, Pageable pageable);
}
