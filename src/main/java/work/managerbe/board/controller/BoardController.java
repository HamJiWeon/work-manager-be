package work.managerbe.board.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import work.managerbe.board.dto.BoardCreateRequest;
import work.managerbe.board.dto.BoardResponse;
import work.managerbe.board.dto.BoardSliceResponse;
import work.managerbe.board.service.BoardService;
import work.managerbe.global.constant.ApiPaths;

@RestController
@RequestMapping(ApiPaths.USER_BASE + ApiPaths.PRJ_CODE + "/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    /**
     * 생성자와 코드로 프로젝트를 식별하고 인증된 요청자의 보드 생성 결과를 반환한다.
     */
    @PostMapping
    public ResponseEntity<BoardResponse> create(
            @PathVariable("userId") UUID creatorId, @PathVariable("code") String code,
            @AuthenticationPrincipal UUID requesterId,
            @Valid @RequestBody BoardCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(boardService.create(creatorId, code, requesterId, request));
    }

    /**
     * 생성자와 코드, 인증된 요청자를 전달하고 페이지 번호와 크기를 검증한다.
     */
    @GetMapping
    public ResponseEntity<BoardSliceResponse> getAll(
            @PathVariable("userId") UUID creatorId, @PathVariable("code") String code,
            @AuthenticationPrincipal UUID requesterId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) int size) {
        return ResponseEntity.ok(boardService.getAll(creatorId, code, requesterId, page, size));
    }
}
