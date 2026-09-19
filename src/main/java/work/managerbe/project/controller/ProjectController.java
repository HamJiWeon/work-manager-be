package work.managerbe.project.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import work.managerbe.project.dto.request.ProjectCreateRequest;
import work.managerbe.project.dto.response.ProjectPageResponse;
import work.managerbe.project.dto.response.ProjectResponse;
import work.managerbe.project.service.ProjectService;

import java.util.UUID;

import static work.managerbe.global.constant.ApiPaths.*;

@RestController
@RequestMapping(USER_BASE)
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping("/projects")
    public ResponseEntity<ProjectResponse> create(
            @PathVariable("userId") UUID userId,
            @RequestBody ProjectCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(userId, request));
    }

    /**
     * 생성자 ID와 코드로 프로젝트를 조회하고 요청자의 활성 멤버십을 확인한다.
     *
     * TODO: 로그인 구현 후 실제 인증 principal 타입에 맞춰 요청자 UUID를 추출하도록 변경한다.
     */
    @GetMapping(PRJ_CODE)
    public ResponseEntity<ProjectResponse> get(
            @PathVariable("userId") UUID creatorId,
            @PathVariable("code") String code,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(projectService.get(creatorId, code, userId));
    }

    @GetMapping("/projects")
    public ResponseEntity<ProjectPageResponse> getAll(
            @PathVariable("userId") UUID userId,
            @RequestParam(defaultValue = "0") int page
    ) {
        return ResponseEntity.ok(projectService.getAll(userId, page));
    }

}
