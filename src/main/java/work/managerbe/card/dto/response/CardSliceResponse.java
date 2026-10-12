package work.managerbe.card.dto.response;

import java.util.List;
import org.springframework.data.domain.Slice;
import work.managerbe.card.domain.Card;

/**
 * 카드 목록과 페이지 번호, 크기 및 다음 데이터 존재 여부를 반환한다.
 */
public record CardSliceResponse(
        List<CardResponse> items,
        int page,
        int size,
        boolean hasNext
) {
    /**
     * 저장소의 슬라이스를 카드 응답 목록과 다음 데이터 존재 여부로 변환한다.
     */
    public static CardSliceResponse from(Slice<Card> cards) {
        return new CardSliceResponse(cards.getContent().stream().map(CardResponse::from).toList(),
                cards.getNumber(), cards.getSize(), cards.hasNext());
    }
}
