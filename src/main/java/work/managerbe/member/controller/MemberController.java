package work.managerbe.member.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import work.managerbe.global.constant.ApiPaths;
import work.managerbe.member.dto.MemberResponse;
import work.managerbe.member.dto.request.MemberCreateRequest;
import work.managerbe.member.service.MemberService;

@RestController
@RequestMapping(ApiPaths.USER_BASE + ApiPaths.PRJ_CODE)
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

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
