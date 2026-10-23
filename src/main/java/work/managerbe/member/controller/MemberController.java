package work.managerbe.member.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import work.managerbe.global.constant.ApiPaths;
import work.managerbe.member.dto.MemberResponse;
import work.managerbe.member.dto.MemberPageResponse;
import work.managerbe.member.dto.request.MemberCreateRequest;
import work.managerbe.member.dto.request.MemberUpdateRequest;
import work.managerbe.member.service.MemberService;

@RestController
@RequestMapping(ApiPaths.USER_BASE + ApiPaths.PRJ_CODE)
@RequiredArgsConstructor
public class MemberController {
    private static final int MAX_PAGE_SIZE = 100;

    private final MemberService memberService;

    /** 명세의 단건 경로로 역할 변경을 요청하고 갱신된 멤버와 감사 시각을 반환한다. */
    @PatchMapping("/{memberId}")
    public ResponseEntity<MemberResponse> update(
            @PathVariable("userId") UUID creatorId, @PathVariable("code") String code,
            @PathVariable("memberId") Long memberId, @AuthenticationPrincipal UUID requesterId,
            @Valid @RequestBody MemberUpdateRequest request
    ) {
        return ResponseEntity.ok(memberService.update(creatorId, code, memberId, requesterId, request));
    }

    /** 멤버 행 삭제 대신 프로젝트 탈퇴를 요청하고 본문 없는 204 응답을 반환한다. */
    @DeleteMapping("/members/{memberId}")
    public ResponseEntity<Void> delete(
            @PathVariable("userId") UUID creatorId, @PathVariable("code") String code,
            @PathVariable("memberId") Long memberId, @AuthenticationPrincipal UUID requesterId
    ) {
        memberService.delete(creatorId, code, memberId, requesterId);
        return ResponseEntity.noContent().build();
    }

    /** 인증된 요청자와 기본 페이지 조건을 전달하고 조회 크기를 1부터 100까지 제한한다. */
    @GetMapping("/members")
    public ResponseEntity<MemberPageResponse> getAll(
            @PathVariable("userId") UUID creatorId, @PathVariable("code") String code,
            @AuthenticationPrincipal UUID requesterId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(memberService.getAll(creatorId, code, requesterId, page, size));
    }

    /** 경로와 인증된 요청자를 전달하고 추가된 멤버를 201로 반환한다. */
    @PostMapping("/members")
    public ResponseEntity<MemberResponse> create(
            @PathVariable("userId") UUID creatorId, @PathVariable("code") String code,
            @AuthenticationPrincipal UUID requesterId, @Valid @RequestBody MemberCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(memberService.create(creatorId, code, requesterId, request));
    }

    /** 명세의 멤버 ID 경로와 인증된 요청자를 전달하고 단건 조회 결과를 반환한다. */
    @GetMapping("/{memberId}")
    public ResponseEntity<MemberResponse> get(
            @PathVariable("userId") UUID creatorId, @PathVariable("code") String code,
            @PathVariable("memberId") Long memberId, @AuthenticationPrincipal UUID requesterId
    ) {
        return ResponseEntity.ok(memberService.get(creatorId, code, memberId, requesterId));
    }
}
