package work.managerbe.card.dto.request;

import java.time.LocalDate;

/**
 * 담당자, 시작일 하한, 종료일 상한과 카드 코드 부분 검색 조건을 전달한다.
 */
public record CardFilterRequest(Long memberId, LocalDate startDate, LocalDate endDate, String code) {

    /**
     * 선택적 조회 조건을 하나의 요청 객체로 묶는다.
     */
    public static CardFilterRequest of(Long memberId, LocalDate startDate, LocalDate endDate, String code) {
        return new CardFilterRequest(memberId, startDate, endDate, code);
    }

    /**
     * 필터가 없는 요청을 생성한다.
     */
    public static CardFilterRequest empty() {
        return of(null, null, null, null);
    }

    /**
     * 멤버 ID는 양수이고 종료일 상한은 시작일 하한보다 앞서지 않아야 한다.
     */
    public boolean isValid() {
        return (memberId == null || memberId > 0)
                && (startDate == null || endDate == null || !endDate.isBefore(startDate));
    }
}
