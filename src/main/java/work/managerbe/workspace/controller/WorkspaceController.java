package work.managerbe.workspace.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import work.managerbe.workspace.dto.request.WorkspaceCreateRequest;
import work.managerbe.workspace.dto.response.WorkspaceResponse;
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
}
