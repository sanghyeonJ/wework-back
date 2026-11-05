package com.wework.core.approval.controller;

import com.wework.core.approval.dto.ApprovalDetailResponse;
import com.wework.core.approval.dto.ApprovalSummaryResponse;
import com.wework.core.approval.service.ApprovalService;
import com.wework.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결재")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    @Operation(
            summary = "내가 처리할 결재 목록",
            description = "기업관리자는 회사 전체의 진행 중인 결재(본인이 신청한 건 제외)를, 일반 사원은 지금 내 차례인 결재를 최신순으로 반환합니다. " +
                    "페이지는 0부터 시작하고 size는 기본 20, 최대 100입니다. sort 값은 무시됩니다(최신순 고정). " +
                    "에러: MEMBER_NOT_FOUND(404, 로그인한 사원이 이 회사에 없음)"
    )
    @GetMapping("/pending")
    public ResponseEntity<Page<ApprovalSummaryResponse>> getPendingList(
            @AuthenticationPrincipal LoginMember loginMember,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
            ) {
        Long memberId = loginMember.getMemberId();
        Long companyId = loginMember.getCompanyId();
        return ResponseEntity.ok(
                approvalService.getPendingList(companyId, memberId, pageable)
        );
    }

    @Operation(
            summary = "내가 처리한 결재 목록",
            description = "내가 승인하거나 반려한 결재를 최신순으로 반환합니다. 기업관리자가 대신 승인·반려한 건도 포함되고, 건너뜀(SKIPPED)과 취소한 건은 포함되지 않습니다. " +
                    "페이지는 0부터 시작하고 size는 기본 20, 최대 100입니다. sort 값은 무시됩니다(최신순 고정). " +
                    "조건에 맞는 건이 없으면 빈 목록을 반환하며, 따로 정한 에러 코드는 없습니다."
    )
    @GetMapping("/processed")
    public ResponseEntity<Page<ApprovalSummaryResponse>> getProcessedList(
            @AuthenticationPrincipal LoginMember loginMember,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        Long memberId = loginMember.getMemberId();
        Long companyId = loginMember.getCompanyId();
        return ResponseEntity.ok(
                approvalService.getProcessedList(companyId, memberId, pageable)
        );
    }

    @Operation(
            summary = "결재 상세 조회",
            description = "결재 요청의 정보와 단계 목록(1단계부터), 취소 정보를 반환합니다. 신청자, 이 결재의 지정 결재자, 기업관리자만 조회할 수 있습니다. " +
                    "에러: MEMBER_NOT_FOUND(404, 로그인한 사원이 이 회사에 없음), APPROVAL_NOT_FOUND(404, 없는 결재이거나 다른 회사의 결재이거나 볼 권한이 없는 경우. 서로 구분하지 않고 같은 응답)"
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApprovalDetailResponse> getDetail(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long id
    ) {
        Long memberId = loginMember.getMemberId();
        Long companyId = loginMember.getCompanyId();
        return ResponseEntity.ok(approvalService.getDetail(companyId, memberId, id));
    }

}
