package work.managerbe.workspace.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import work.managerbe.workspace.dto.request.WorkspaceCreateRequest;
import work.managerbe.workspace.dto.request.WorkspaceUpdateRequest;
import work.managerbe.workspace.dto.response.WorkspaceResponse;
import work.managerbe.workspace.dto.response.WorkspaceSliceResponse;
import work.managerbe.workspace.service.WorkspaceService;

import java.util.UUID;

import static work.managerbe.global.constant.ApiPaths.PRJ_CODE;
import static work.managerbe.global.constant.ApiPaths.USER_BASE;

@RestController
@RequestMapping(USER_BASE + PRJ_CODE + "/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(
            @PathVariable("userId") UUID userId,
            @PathVariable("code") String code,
            @AuthenticationPrincipal UUID requesterId,
            @RequestBody WorkspaceCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceService.create(userId, code, requesterId, request));
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> get(
            @PathVariable("userId") UUID userId,
            @PathVariable("code") String code,
            @PathVariable("workspaceId") Long workspaceId,
            @AuthenticationPrincipal UUID requesterId
    ) {
        return ResponseEntity.ok().body(workspaceService.get(userId, code, workspaceId, requesterId));
    }

    @GetMapping
    public ResponseEntity<WorkspaceSliceResponse> getAll(
            @PathVariable("userId") UUID userId,
            @PathVariable("code") String code,
            @AuthenticationPrincipal UUID requesterId,
            @RequestParam(name = "page", defaultValue = "0") int page
    ) {
        return ResponseEntity.ok().body(workspaceService.getAll(userId, code, requesterId, page));
    }

    @PatchMapping("/{workspaceId}")
    public ResponseEntity<WorkspaceResponse> update(
            @PathVariable("userId") UUID userId,
            @PathVariable("code") String code,
            @PathVariable("workspaceId") Long workspaceId,
            @AuthenticationPrincipal UUID requesterId,
            @RequestBody WorkspaceUpdateRequest request
    ) {
        return ResponseEntity.ok().body(workspaceService.update(userId, code, workspaceId, requesterId, request));
    }
}
