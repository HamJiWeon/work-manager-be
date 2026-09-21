package work.managerbe.board.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import work.managerbe.board.dto.request.BoardCreateRequest;
import work.managerbe.board.dto.response.BoardResponse;
import work.managerbe.board.dto.request.BoardUpdateRequest;
import work.managerbe.board.dto.response.BoardSliceResponse;
import work.managerbe.board.service.BoardService;
import work.managerbe.global.constant.ApiPaths;

@RestController
@RequestMapping(ApiPaths.USER_BASE + ApiPaths.PRJ_CODE)
@RequiredArgsConstructor
public class BoardController {

    private static final int MAX_PAGE_SIZE = 100;

    private final BoardService boardService;

    /**
     * 생성자와 코드로 프로젝트를 식별하고 인증된 요청자의 보드 생성 결과를 반환한다.
     */
    @PostMapping("/boards")
    public ResponseEntity<BoardResponse> create(
            @PathVariable("userId") UUID creatorId, @PathVariable("code") String code,
            @AuthenticationPrincipal(expression = "userId") UUID requesterId,
            @Valid @RequestBody BoardCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(boardService.create(creatorId, code, requesterId, request));
    }

    /**
     * 생성자와 코드, 인증된 요청자를 전달하고 조회 크기를 1부터 100까지 제한한다.
     */
    @GetMapping("/boards")
    public ResponseEntity<BoardSliceResponse> getAll(
            @PathVariable("userId") UUID creatorId, @PathVariable("code") String code,
            @AuthenticationPrincipal(expression = "userId") UUID requesterId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(boardService.getAll(creatorId, code, requesterId, page, size));
    }
    /**
     * 요청 배열의 순서대로 프로젝트 보드를 재배치하고 전달된 이름을 함께 수정한다.
     */
    @PatchMapping("/boards")
    public ResponseEntity<List<BoardResponse>> update(
            @PathVariable("userId") UUID creatorId,
            @PathVariable("code") String code,
            @AuthenticationPrincipal(expression = "userId") UUID requesterId,
            @RequestBody BoardUpdateRequest request
    ) {
        return ResponseEntity.ok(boardService.update(creatorId, code, requesterId, request));
    }
}
