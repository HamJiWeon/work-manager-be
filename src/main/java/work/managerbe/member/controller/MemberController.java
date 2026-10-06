package work.managerbe.member.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import work.managerbe.member.dto.*;
import work.managerbe.member.service.MemberService;

/** 명세의 축약 경로와 users/projects 경로에서 인증 principal을 서비스에 전달한다. */
@RestController
@RequestMapping({"/{userId}/{projectId}", "/users/{userId}/projects/{projectId}"})
@RequiredArgsConstructor
public class MemberController {
    private static final int MAX_PAGE_SIZE = 100;
    private final MemberService memberService;

    /** 역할을 생략한 추가 요청은 MEMBER로 생성하고 201을 반환한다. */
    @PostMapping("/members")
    public ResponseEntity<MemberResponse> create(@PathVariable UUID userId, @PathVariable Long projectId,
            @AuthenticationPrincipal UUID requesterId, @Valid @RequestBody MemberCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.create(userId, projectId, requesterId, request));
    }

    /** 활성 멤버 단건 조회 결과를 200으로 반환한다. */
    @GetMapping({"/{memberId}", "/members/{memberId}"})
    public ResponseEntity<MemberResponse> get(@PathVariable UUID userId, @PathVariable Long projectId,
            @PathVariable Long memberId, @AuthenticationPrincipal UUID requesterId) {
        return ResponseEntity.ok(memberService.get(userId, projectId, requesterId, memberId));
    }

    /** 기본 20개 단위로 활성 멤버 페이지와 전체 개수를 반환한다. */
    @GetMapping("/members")
    public ResponseEntity<MemberPageResponse> getAll(@PathVariable UUID userId, @PathVariable Long projectId,
            @AuthenticationPrincipal UUID requesterId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return ResponseEntity.ok(memberService.getAll(userId, projectId, requesterId, page, size));
    }

    /** OWNER가 요청한 일반 역할 변경 결과를 반환한다. */
    @PatchMapping({"/{memberId}", "/members/{memberId}"})
    public ResponseEntity<MemberResponse> update(@PathVariable UUID userId, @PathVariable Long projectId,
            @PathVariable Long memberId, @AuthenticationPrincipal UUID requesterId,
            @Valid @RequestBody MemberUpdateRequest request) {
        return ResponseEntity.ok(memberService.update(userId, projectId, requesterId, memberId, request));
    }

    /** 탈퇴 처리 후 본문 없는 204 응답을 반환한다. */
    @DeleteMapping("/members/{memberId}")
    public ResponseEntity<Void> delete(@PathVariable UUID userId, @PathVariable Long projectId,
            @PathVariable Long memberId, @AuthenticationPrincipal UUID requesterId) {
        memberService.delete(userId, projectId, requesterId, memberId);
        return ResponseEntity.noContent().build();
    }
}
