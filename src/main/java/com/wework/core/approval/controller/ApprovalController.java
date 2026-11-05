package com.wework.core.approval.controller;

import com.wework.core.approval.dto.ApprovalActionRequest;
import com.wework.core.approval.dto.ApprovalDetailResponse;
import com.wework.core.approval.dto.ApprovalSummaryResponse;
import com.wework.core.approval.service.ApprovalService;
import com.wework.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
        return ResponseEntity.ok(
                approvalService.getPendingList(loginMember.getCompanyId(), loginMember.getMemberId(), pageable)
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
        return ResponseEntity.ok(
                approvalService.getProcessedList(loginMember.getCompanyId(), loginMember.getMemberId(), pageable)
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
        return ResponseEntity.ok(approvalService.getDetail(loginMember.getCompanyId(), loginMember.getMemberId(), id));
    }

    @Operation(
            summary = "결재 승인",
            description = "지금 내 차례인 결재자가 승인합니다. 마지막 단계가 아니면 다음 단계가 내 차례가 되고, 마지막 단계면 결재가 최종 승인(APPROVED)됩니다. " +
                    "기업관리자는 지정 결재자가 아니거나 내 차례가 아니어도 승인할 수 있고, 이때는 남은 단계를 건너뛰고 바로 최종 승인됩니다(대리 승인). " +
                    "본문은 필수이며 comment는 선택(최대 500자)입니다. comment가 없으면 {}를 보냅니다. 성공하면 본문 없이 200을 반환합니다. " +
                    "에러: MEMBER_NOT_FOUND(404, 로그인한 사원이 이 회사에 없음), APPROVAL_NOT_FOUND(404, 없는 결재이거나 다른 회사의 결재), " +
                    "APPROVAL_ALREADY_CLOSED(409, 이미 끝난 결재), CANNOT_PROCESS_OWN_REQUEST(403, 본인이 신청한 결재), " +
                    "NOT_YOUR_TURN(409, 결재선에 있지만 지금 내 차례가 아니거나 이미 처리함), NOT_APPROVER(403, 이 결재의 결재자도 기업관리자도 아님), " +
                    "CONCURRENT_MODIFICATION(409, 다른 사용자가 동시에 먼저 처리함), INVALID_INPUT(400, comment가 500자 초과이거나 본문이 없음)"
    )
    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long id,
            @Valid @RequestBody ApprovalActionRequest request
            ) {
        approvalService.approve(loginMember.getCompanyId(), loginMember.getMemberId(), id, request.getComment());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "결재 반려",
            description = "지금 내 차례인 결재자가 반려합니다. 내 단계는 반려(REJECTED), 아직 차례가 오지 않은 단계는 취소(CANCELED)로 바뀌고 결재는 즉시 반려(REJECTED)로 끝납니다. " +
                    "기업관리자는 지정 결재자가 아니거나 내 차례가 아니어도 대신 반려할 수 있으며 결과는 같습니다. " +
                    "본문은 필수이며 comment는 선택(최대 500자)입니다. comment가 없으면 {}를 보냅니다. 성공하면 본문 없이 200을 반환합니다. " +
                    "에러: MEMBER_NOT_FOUND(404, 로그인한 사원이 이 회사에 없음), APPROVAL_NOT_FOUND(404, 없는 결재이거나 다른 회사의 결재), " +
                    "APPROVAL_ALREADY_CLOSED(409, 이미 끝난 결재), CANNOT_PROCESS_OWN_REQUEST(403, 본인이 신청한 결재), " +
                    "NOT_YOUR_TURN(409, 결재선에 있지만 지금 내 차례가 아니거나 이미 처리함), NOT_APPROVER(403, 이 결재의 결재자도 기업관리자도 아님), " +
                    "CONCURRENT_MODIFICATION(409, 다른 사용자가 동시에 먼저 처리함), INVALID_INPUT(400, comment가 500자 초과이거나 본문이 없음)"
    )
    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long id,
            @Valid @RequestBody ApprovalActionRequest request
    ) {
        approvalService.reject(loginMember.getCompanyId(), loginMember.getMemberId(), id, request.getComment());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "결재 취소",
            description = "결재를 취소합니다. 신청자 본인은 진행 중(PENDING)인 결재만 취소할 수 있고 사유는 선택입니다. " +
                    "기업관리자는 진행 중이거나 승인 완료된 결재를 취소할 수 있으며 사유가 필수입니다. 본인이 신청한 결재는 기업관리자여도 신청자 규칙(진행 중인 결재만)이 적용됩니다. " +
                    "취소하면 결재는 CANCELED가 되고, 아직 처리되지 않은 단계만 취소(CANCELED)됩니다(이미 승인한 단계는 기록으로 남음). " +
                    "취소자와 취소 시각, 사유가 기록됩니다. comment가 취소 사유입니다(최대 500자). 본문은 필수이며 사유가 없으면 {}를 보냅니다. 성공하면 본문 없이 200을 반환합니다. " +
                    "에러: MEMBER_NOT_FOUND(404, 로그인한 사원이 이 회사에 없음), APPROVAL_NOT_FOUND(404, 없는 결재이거나 다른 회사의 결재), " +
                    "APPROVAL_ALREADY_CLOSED(409, 이미 반려되었거나 취소된 결재), CANCEL_NOT_ALLOWED(409, 신청자가 승인 완료된 결재를 취소함. 기업관리자만 가능), " +
                    "CANCEL_REASON_REQUIRED(400, 기업관리자가 사유 없이 취소함), NOT_APPROVER(403, 신청자도 기업관리자도 아님), " +
                    "CONCURRENT_MODIFICATION(409, 다른 사용자가 동시에 먼저 처리함), INVALID_INPUT(400, comment가 500자 초과이거나 본문이 없음)"
    )
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long id,
            @Valid @RequestBody ApprovalActionRequest request
    ) {
        approvalService.cancel(loginMember.getCompanyId(), loginMember.getMemberId(), id, request.getComment());
        return ResponseEntity.ok().build();
    }

}
