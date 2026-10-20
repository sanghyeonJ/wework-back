package com.wework.core.member;

import com.wework.core.member.dto.*;
import com.wework.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "기업관리자 - 사원")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/company/members")
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "사원 목록", description = "status·departmentId·keyword(이름·이메일)는 선택, 페이징")
    @GetMapping
    public ResponseEntity<Page<MemberResponse>> getMembers(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestParam(required = false) MemberStatus status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "false") boolean includeSubDepartments,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)Pageable pageable
            ) {
        return ResponseEntity.ok(
                memberService.getMembers(loginMember.getCompanyId(), status, departmentId, includeSubDepartments, keyword, pageable)
        );
    }

    @Operation(summary = "사원 상세")
    @GetMapping("/{memberId}")
    public ResponseEntity<MemberResponse> getMember(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long memberId
    ) {
        return ResponseEntity.ok(memberService.getMember(loginMember.getCompanyId(), memberId));
    }

    @Operation(summary = "사원 계정 발급", description = "EMPLOYEE·INVITED로 생성, 응답에 초대 링크 (M6 전 테스트용)")
    @PostMapping
    public ResponseEntity<MemberInviteResponse> createMember(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody MemberCreateRequest request
            ) {
        MemberInviteResponse response = memberService.createMember(loginMember.getCompanyId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "사원 수정", description = "모든 값을 함께 보냄 (null이면 해제), 부서장이면 부서 변경 거절")
    @PatchMapping("/{memberId}")
    public ResponseEntity<MemberResponse> updateMember(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long memberId,
            @Valid @RequestBody MemberUpdateRequest request
            ) {
        return ResponseEntity.ok(
                memberService.updateMember(loginMember.getCompanyId(), memberId, request)
        );
    }

    @Operation(summary = "권한 변경", description = "COMPANY_ADMIN 또는 EMPLOYEE, 기업관리자는 최소 1명 유지")
    @PatchMapping("/{memberId}/role")
    public ResponseEntity<MemberResponse> changeRole(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long memberId,
            @Valid @RequestBody MemberRoleRequest request
            ) {
        return ResponseEntity.ok(
                memberService.changeRole(loginMember.getCompanyId(), memberId, request)
        );
    }

    @Operation(summary = "퇴사 처리", description = "부서장이면 거절, 리프레시 토큰 전부 삭제")
    @PostMapping("/{memberId}/resign")
    public ResponseEntity<MemberResponse> resignMember(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long memberId
    ) {
        return ResponseEntity.ok(memberService.resignMember(loginMember.getCompanyId(), memberId));
    }

    @Operation(summary = "초대 재발급", description = "INVITED 사원만, 이전 초대 링크는 무효화")
    @PostMapping("/{memberId}/invitation")
    public ResponseEntity<MemberInviteResponse> reissueInvitation(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long memberId
    ) {
        return ResponseEntity.ok(memberService.reissueInvitation(loginMember.getCompanyId(), memberId));
    }

}
