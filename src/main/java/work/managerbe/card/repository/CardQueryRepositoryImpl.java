package work.managerbe.card.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import work.managerbe.card.dto.request.CardFilterRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import work.managerbe.card.domain.Card;
import work.managerbe.card.domain.QCard;

/**
 * QueryDSL의 동적 조건으로 필터를 조합하고 추가 한 건으로 다음 목록 존재 여부를 확인한다.
 */
@RequiredArgsConstructor
public class CardQueryRepositoryImpl implements CardQueryRepository {
    private static final int NEXT_ITEM_COUNT = 1;
    private final JPAQueryFactory queryFactory;

    /**
     * 요청한 크기보다 한 건 더 조회하고 전체 개수 쿼리 없이 슬라이스를 생성한다.
     */
    @Override
    public Slice<Card> findSlice(Long projectId, Long boardId, CardFilterRequest filter, Pageable pageable) {

        QCard card = QCard.card;
        BooleanBuilder conditions = new BooleanBuilder()
                .and(card.project.id.eq(projectId))
                .and(card.board.id.eq(boardId));

        if (filter.memberId() != null) {
            conditions.and(card.member.id.eq(filter.memberId()));
        }

        if (filter.startDate() != null) {
            conditions.and(card.startDate.goe(filter.startDate()));
        }

        if (filter.endDate() != null) {
            conditions.and(card.endDate.loe(filter.endDate()));
        }

        if (filter.code() != null && !filter.code().isBlank()) {
            conditions.and(card.project.code.concat("-")
                    .concat(card.id.stringValue()).containsIgnoreCase(filter.code().strip()));
        }

        var primaryOrder = filter.startDate() != null ? card.startDate.asc()
                : filter.endDate() != null ? card.endDate.asc() : card.sortOrder.asc();

        List<Card> result = queryFactory.selectFrom(card)
                .where(conditions)
                .orderBy(primaryOrder, card.id.asc())
                .offset(pageable.getOffset())
                .limit((long) pageable.getPageSize() + NEXT_ITEM_COUNT)
                .fetch();

        boolean hasNext = result.size() > pageable.getPageSize();

        List<Card> content = hasNext ? result.subList(0, pageable.getPageSize()) : result;

        return new SliceImpl<>(List.copyOf(content), pageable, hasNext);
    }
}
