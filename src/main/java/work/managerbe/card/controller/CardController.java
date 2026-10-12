package work.managerbe.card.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import work.managerbe.card.dto.request.CardFilterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import work.managerbe.card.dto.request.CardCreateRequest;
import work.managerbe.card.dto.request.CardUpdateRequest;
import work.managerbe.card.dto.response.CardResponse;
import work.managerbe.card.dto.response.CardSliceResponse;
import work.managerbe.card.service.CardService;
import work.managerbe.global.constant.ApiPaths;

@RestController
@RequestMapping(ApiPaths.USER_BASE + ApiPaths.PRJ_CODE + "/{boardId}/cards")
@RequiredArgsConstructor
public class CardController {
    private static final int MAX_PAGE_SIZE = 100;

    private final CardService cardService;

    /**
     * 담당자·기간·카드 코드 필터를 전달하고 페이지 크기를 1부터 100까지 제한한다.
     */
    @GetMapping
    public ResponseEntity<CardSliceResponse> getAll(
            @PathVariable("userId") UUID creatorId, @PathVariable("code") String code,
            @PathVariable("boardId") Long boardId, @AuthenticationPrincipal UUID requesterId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(name = "memberId", required = false) @Min(1) Long memberId,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "code", required = false) String cardCode
    ) {
        return ResponseEntity.ok(cardService.getAll(creatorId, code, boardId, requesterId, page, size,
                CardFilterRequest.of(memberId, startDate, endDate, cardCode)));
    }


    /**
     * 경로와 인증된 요청자를 서비스에 전달하고 단건 카드 정보를 반환한다.
     */
    @GetMapping("/{cardId}")
    public ResponseEntity<CardResponse> get(
            @PathVariable("userId") UUID creatorId, @PathVariable("code") String code,
            @PathVariable("boardId") Long boardId, @PathVariable("cardId") Long cardId,
            @AuthenticationPrincipal UUID requesterId
    ) {
        return ResponseEntity.ok(cardService.get(creatorId, code, boardId, cardId, requesterId));
    }

    /**
     * 경로와 인증된 요청자를 서비스에 전달하고 생성된 카드를 201로 반환한다.
     */
    @PostMapping
    public ResponseEntity<CardResponse> create(
            @PathVariable("userId") UUID creatorId, @PathVariable("code") String code,
            @PathVariable("boardId") Long boardId, @AuthenticationPrincipal UUID requesterId,
            @Valid @RequestBody CardCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cardService.create(creatorId, code, boardId, requesterId, request));
    }
    /**
     * 현재 보드 경로의 카드를 부분 수정하며 상태와 순서를 함께 저장한다.
     */
    @PatchMapping("/{cardId}")
    public ResponseEntity<CardResponse> update(
            @PathVariable("userId") UUID creatorId, @PathVariable("code") String code,
            @PathVariable("boardId") Long boardId, @PathVariable("cardId") Long cardId,
            @AuthenticationPrincipal UUID requesterId, @RequestBody CardUpdateRequest request
    ) {
        return ResponseEntity.ok(cardService.update(creatorId, code, boardId, cardId, requesterId, request));
    }

}
