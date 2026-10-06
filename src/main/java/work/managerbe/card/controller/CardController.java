package work.managerbe.card.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import work.managerbe.card.dto.request.CardCreateRequest;
import work.managerbe.card.dto.response.CardResponse;
import work.managerbe.card.service.CardService;
import work.managerbe.global.constant.ApiPaths;

@RestController
@RequestMapping(ApiPaths.USER_BASE + ApiPaths.PRJ_CODE + "/{boardId}/cards")
@RequiredArgsConstructor
public class CardController {
    private final CardService cardService;

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
}
