package work.managerbe.board.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import work.managerbe.board.dto.BoardCreateRequest;
import work.managerbe.board.dto.BoardResponse;
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
}
