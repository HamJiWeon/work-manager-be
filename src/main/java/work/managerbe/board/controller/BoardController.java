package work.managerbe.board.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import work.managerbe.board.dto.BoardCreateRequest;
import work.managerbe.board.dto.BoardResponse;
import work.managerbe.board.dto.BoardPageResponse;
import work.managerbe.board.service.BoardService;
import work.managerbe.global.constant.ApiPaths;

@RestController
@RequestMapping(ApiPaths.USER_BASE + ApiPaths.PRJ_CODE + "/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    /**
     * 보드 이름을 검증하고 생성 결과를 201 상태와 함께 반환한다.
     */
    @PostMapping
    public ResponseEntity<BoardResponse> create(
            @PathVariable("userId") UUID userId, @PathVariable("code") String code,
            @Valid @RequestBody BoardCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(boardService.create(userId, code, request));
    }

    /**
     * 페이지 번호와 크기를 검증하고 프로젝트의 보드 목록을 반환한다.
     */
    @GetMapping
    public ResponseEntity<BoardPageResponse> getAll(
            @PathVariable("userId") UUID userId, @PathVariable("code") String code,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return ResponseEntity.ok(boardService.getAll(userId, code, page, size));
    }
}
