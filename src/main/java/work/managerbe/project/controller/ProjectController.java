package work.managerbe.project.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import work.managerbe.project.dto.request.ProjectCreateRequest;
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

    @GetMapping(PRJ_CODE)
    public ResponseEntity<ProjectResponse> get(
            @PathVariable UUID userId,
            @PathVariable String code) {
        return ResponseEntity.ok(projectService.get(userId, code));
    }
}
