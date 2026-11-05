package com.wework.core.approval;

import com.wework.core.approval.entity.ApprovalRequest;
import com.wework.core.approval.entity.ApprovalRequestStatus;
import com.wework.core.approval.entity.ApprovalStep;
import com.wework.core.approval.entity.ApprovalStepStatus;
import com.wework.core.approval.error.ApprovalErrorCode;
import com.wework.core.approval.event.ApprovalCompletedEvent;
import com.wework.core.member.entity.Member;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalProcessTest extends ApprovalTestSupport {

    private void approve(Member who, Long id) {
        approvalService.approve(company.getId(), who.getId(), id, null);
    }

    private void reject(Member who, Long id) {
        approvalService.reject(company.getId(), who.getId(), id, null);
    }

    private void cancel(Member who, Long id, String reason) {
        approvalService.cancel(company.getId(), who.getId(), id, reason);
    }

    // ---------------- 승인 ----------------

    @Test
    void 중간_단계_승인은_다음_단계를_내_차례로_만들고_이벤트는_없다() {
        Long id = createApproval(requester, kim, park);

        approvalService.approve(company.getId(), kim.getId(), id, "checked");

        List<ApprovalStep> steps = stepsOf(id);
        assertEquals(ApprovalStepStatus.APPROVED, steps.get(0).getStatus());
        assertEquals(kim.getId(), steps.get(0).getProcessedBy().getId());
        assertEquals("checked", steps.get(0).getComment());
        assertNotNull(steps.get(0).getProcessedAt());
        assertEquals(ApprovalStepStatus.PENDING, steps.get(1).getStatus());
        assertEquals(ApprovalRequestStatus.PENDING, requestOf(id).getStatus());
        assertTrue(completedEvents().isEmpty());
    }

    @Test
    void 마지막_단계_승인은_요청을_승인으로_끝내고_이벤트를_한_번_보낸다() {
        Long id = createApproval(requester, kim, park);

        approve(kim, id);
        approve(park, id);

        assertEquals(ApprovalStepStatus.APPROVED, stepsOf(id).get(1).getStatus());
        assertEquals(ApprovalRequestStatus.APPROVED, requestOf(id).getStatus());
        List<ApprovalCompletedEvent> events = completedEvents();
        assertEquals(1, events.size());
        assertEvent(events.get(0), id, ApprovalRequestStatus.APPROVED, ApprovalRequestStatus.PENDING);
    }

    @Test
    void 결재자가_한_명이면_그_승인이_바로_최종_승인이다() {
        Long id = createApproval(requester, kim);

        approve(kim, id);

        assertEquals(ApprovalRequestStatus.APPROVED, requestOf(id).getStatus());
        assertEquals(1, completedEvents().size());
    }

    @Test
    void 기업관리자_대리_승인은_막힌_단계는_승인_남은_단계는_건너뜀_요청은_최종_승인이다() {
        Long id = createApproval(requester, kim, park);

        approve(admin, id);

        List<ApprovalStep> steps = stepsOf(id);
        assertEquals(ApprovalStepStatus.APPROVED, steps.get(0).getStatus());
        assertEquals(kim.getId(), steps.get(0).getApprover().getId());
        assertEquals(admin.getId(), steps.get(0).getProcessedBy().getId());
        assertEquals(ApprovalStepStatus.SKIPPED, steps.get(1).getStatus());
        assertEquals(admin.getId(), steps.get(1).getProcessedBy().getId());
        assertEquals(ApprovalRequestStatus.APPROVED, requestOf(id).getStatus());
        assertEquals(1, completedEvents().size());
    }

    @Test
    void 기업관리자가_지정_결재자이고_내_차례면_일반_승인으로_그_단계만_처리한다() {
        Long id = createApproval(requester, admin, park);

        approve(admin, id);

        List<ApprovalStep> steps = stepsOf(id);
        assertEquals(ApprovalStepStatus.APPROVED, steps.get(0).getStatus());
        assertEquals(ApprovalStepStatus.PENDING, steps.get(1).getStatus());
        assertEquals(ApprovalRequestStatus.PENDING, requestOf(id).getStatus());
        assertTrue(completedEvents().isEmpty());
    }

    @Test
    void 결재선에_있지만_내_차례가_아니면_NOT_YOUR_TURN이고_아무것도_바뀌지_않는다() {
        Long id = createApproval(requester, kim, park);

        assertErrorCode(ApprovalErrorCode.NOT_YOUR_TURN, () -> approve(park, id));

        assertEquals(ApprovalStepStatus.PENDING, stepsOf(id).get(0).getStatus());
        assertEquals(ApprovalStepStatus.WAITING, stepsOf(id).get(1).getStatus());
    }

    @Test
    void 이미_내_단계를_처리한_결재자가_다시_승인하면_NOT_YOUR_TURN() {
        Long id = createApproval(requester, kim, park);
        approve(kim, id);

        assertErrorCode(ApprovalErrorCode.NOT_YOUR_TURN, () -> approve(kim, id));
    }

    @Test
    void 결재선에_없는_사원은_NOT_APPROVER() {
        Long id = createApproval(requester, kim, park);

        assertErrorCode(ApprovalErrorCode.NOT_APPROVER, () -> approve(outsider, id));
        assertErrorCode(ApprovalErrorCode.NOT_APPROVER, () -> reject(outsider, id));
    }

    @Test
    void 본인이_신청한_결재는_기업관리자도_승인_반려_못_한다() {
        Long id = createApproval(admin, kim);

        assertErrorCode(ApprovalErrorCode.CANNOT_PROCESS_OWN_REQUEST, () -> approve(admin, id));
        assertErrorCode(ApprovalErrorCode.CANNOT_PROCESS_OWN_REQUEST, () -> reject(admin, id));
        assertEquals(ApprovalRequestStatus.PENDING, requestOf(id).getStatus());

        approve(kim, id);
        assertEquals(ApprovalRequestStatus.APPROVED, requestOf(id).getStatus());
    }

    @Test
    void 이미_끝난_결재를_또_처리하면_APPROVAL_ALREADY_CLOSED이고_이벤트는_늘지_않는다() {
        Long id = createApproval(requester, kim);
        approve(kim, id);

        assertErrorCode(ApprovalErrorCode.APPROVAL_ALREADY_CLOSED, () -> approve(kim, id));
        assertErrorCode(ApprovalErrorCode.APPROVAL_ALREADY_CLOSED, () -> approve(admin, id));
        assertErrorCode(ApprovalErrorCode.APPROVAL_ALREADY_CLOSED, () -> reject(admin, id));
        assertEquals(1, completedEvents().size());
    }

    // ---------------- 반려 ----------------

    @Test
    void 반려는_내_단계_반려_남은_단계_취소_요청_반려로_즉시_끝나고_이벤트를_보낸다() {
        Long id = createApproval(requester, kim, park);

        approvalService.reject(company.getId(), kim.getId(), id, "no thanks");

        List<ApprovalStep> steps = stepsOf(id);
        assertEquals(ApprovalStepStatus.REJECTED, steps.get(0).getStatus());
        assertEquals(kim.getId(), steps.get(0).getProcessedBy().getId());
        assertEquals("no thanks", steps.get(0).getComment());
        assertEquals(ApprovalStepStatus.CANCELED, steps.get(1).getStatus());
        assertNull(steps.get(1).getProcessedBy());
        assertEquals(ApprovalRequestStatus.REJECTED, requestOf(id).getStatus());
        List<ApprovalCompletedEvent> events = completedEvents();
        assertEquals(1, events.size());
        assertEvent(events.get(0), id, ApprovalRequestStatus.REJECTED, ApprovalRequestStatus.PENDING);
    }

    @Test
    void 기업관리자_대리_반려는_처리자만_관리자이고_결과는_같다() {
        Long id = createApproval(requester, kim, park);

        reject(admin, id);

        List<ApprovalStep> steps = stepsOf(id);
        assertEquals(ApprovalStepStatus.REJECTED, steps.get(0).getStatus());
        assertEquals(kim.getId(), steps.get(0).getApprover().getId());
        assertEquals(admin.getId(), steps.get(0).getProcessedBy().getId());
        assertEquals(ApprovalStepStatus.CANCELED, steps.get(1).getStatus());
        assertEquals(ApprovalRequestStatus.REJECTED, requestOf(id).getStatus());
    }

    @Test
    void 앞_단계가_이미_승인했으면_그_기록은_유지하고_뒤_단계가_반려한다() {
        Long id = createApproval(requester, kim, park);
        approve(kim, id);

        reject(park, id);

        List<ApprovalStep> steps = stepsOf(id);
        assertEquals(ApprovalStepStatus.APPROVED, steps.get(0).getStatus());
        assertEquals(ApprovalStepStatus.REJECTED, steps.get(1).getStatus());
        assertEquals(ApprovalRequestStatus.REJECTED, requestOf(id).getStatus());
    }

    @Test
    void 반려한_결재를_다시_처리하면_APPROVAL_ALREADY_CLOSED() {
        Long id = createApproval(requester, kim, park);
        reject(kim, id);

        assertErrorCode(ApprovalErrorCode.APPROVAL_ALREADY_CLOSED, () -> approve(admin, id));
        assertErrorCode(ApprovalErrorCode.APPROVAL_ALREADY_CLOSED, () -> reject(kim, id));
        assertEquals(1, completedEvents().size());
    }

    @Test
    void 결재자가_한_명일_때_반려하면_단계와_요청이_반려된다() {
        Long id = createApproval(requester, kim);

        reject(kim, id);

        assertEquals(ApprovalStepStatus.REJECTED, stepsOf(id).get(0).getStatus());
        assertEquals(ApprovalRequestStatus.REJECTED, requestOf(id).getStatus());
    }

    // ---------------- 취소 ----------------

    @Test
    void 신청자는_진행_중인_결재를_사유_없이_취소할_수_있다() {
        Long id = createApproval(requester, kim, park);

        cancel(requester, id, null);

        ApprovalRequest request = requestOf(id);
        assertEquals(ApprovalRequestStatus.CANCELED, request.getStatus());
        assertEquals(requester.getId(), request.getCanceledBy().getId());
        assertNotNull(request.getCanceledAt());
        assertNull(request.getCancelReason());
        for (ApprovalStep step : stepsOf(id)) {
            assertEquals(ApprovalStepStatus.CANCELED, step.getStatus());
        }
        List<ApprovalCompletedEvent> events = completedEvents();
        assertEquals(1, events.size());
        assertEvent(events.get(0), id, ApprovalRequestStatus.CANCELED, ApprovalRequestStatus.PENDING);
    }

    @Test
    void 일부_승인한_뒤_취소하면_승인한_단계는_기록으로_남고_남은_단계만_취소된다() {
        Long id = createApproval(requester, kim, park);
        approve(kim, id);

        cancel(requester, id, null);

        List<ApprovalStep> steps = stepsOf(id);
        assertEquals(ApprovalStepStatus.APPROVED, steps.get(0).getStatus());
        assertEquals(ApprovalStepStatus.CANCELED, steps.get(1).getStatus());
        assertEquals(ApprovalRequestStatus.CANCELED, requestOf(id).getStatus());
    }

    @Test
    void 신청자는_승인_완료된_결재를_취소할_수_없다() {
        Long id = createApproval(requester, kim);
        approve(kim, id);

        assertErrorCode(ApprovalErrorCode.CANCEL_NOT_ALLOWED, () -> cancel(requester, id, "please"));

        assertEquals(ApprovalRequestStatus.APPROVED, requestOf(id).getStatus());
        assertEquals(1, completedEvents().size());
    }

    // 버그 예방: 취소 직전 상태를 바꾸기 전에 저장하지 않으면 이벤트의 직전 상태가 CANCELED로 나가 연차 복구를 판단할 수 없다
    @Test
    void 기업관리자는_승인_완료된_결재도_사유와_함께_취소하고_이벤트에_직전_상태가_담긴다() {
        Long id = createApproval(requester, kim);
        approve(kim, id);

        cancel(admin, id, "wrong approval");

        ApprovalRequest request = requestOf(id);
        assertEquals(ApprovalRequestStatus.CANCELED, request.getStatus());
        assertEquals(admin.getId(), request.getCanceledBy().getId());
        assertEquals("wrong approval", request.getCancelReason());
        assertEquals(ApprovalStepStatus.APPROVED, stepsOf(id).get(0).getStatus());

        List<ApprovalCompletedEvent> events = completedEvents();
        assertEquals(2, events.size());
        assertEvent(events.get(0), id, ApprovalRequestStatus.APPROVED, ApprovalRequestStatus.PENDING);
        assertEvent(events.get(1), id, ApprovalRequestStatus.CANCELED, ApprovalRequestStatus.APPROVED);
    }

    @Test
    void 기업관리자_취소는_사유가_필수다_null_빈_문자열_공백은_거절하고_사유가_있으면_성공한다() {
        Long id = createApproval(requester, kim);

        assertErrorCode(ApprovalErrorCode.CANCEL_REASON_REQUIRED, () -> cancel(admin, id, null));
        assertErrorCode(ApprovalErrorCode.CANCEL_REASON_REQUIRED, () -> cancel(admin, id, ""));
        assertErrorCode(ApprovalErrorCode.CANCEL_REASON_REQUIRED, () -> cancel(admin, id, "   "));
        assertEquals(ApprovalRequestStatus.PENDING, requestOf(id).getStatus());

        cancel(admin, id, "reason");
        assertEquals(ApprovalRequestStatus.CANCELED, requestOf(id).getStatus());
    }

    @Test
    void 신청자도_기업관리자도_아니면_NOT_APPROVER다_결재자도_마찬가지다() {
        Long id = createApproval(requester, kim, park);

        assertErrorCode(ApprovalErrorCode.NOT_APPROVER, () -> cancel(kim, id, "reason"));
        assertErrorCode(ApprovalErrorCode.NOT_APPROVER, () -> cancel(outsider, id, "reason"));
        assertEquals(ApprovalRequestStatus.PENDING, requestOf(id).getStatus());
    }

    @Test
    void 이미_반려되거나_취소된_결재는_취소할_수_없고_이벤트는_늘지_않는다() {
        Long rejected = createApproval(requester, kim);
        reject(kim, rejected);
        Long canceled = createApproval(requester, kim);
        cancel(requester, canceled, null);

        assertErrorCode(ApprovalErrorCode.APPROVAL_ALREADY_CLOSED, () -> cancel(admin, rejected, "reason"));
        assertErrorCode(ApprovalErrorCode.APPROVAL_ALREADY_CLOSED, () -> cancel(requester, canceled, null));
        assertErrorCode(ApprovalErrorCode.APPROVAL_ALREADY_CLOSED, () -> cancel(admin, canceled, "reason"));
        assertEquals(2, completedEvents().size());
    }

    @Test
    void 기업관리자가_본인이_신청한_결재는_신청자_규칙을_따른다_진행_중이면_사유_없이_취소_승인_후면_취소_불가() {
        Long pending = createApproval(admin, kim);
        cancel(admin, pending, null);
        assertEquals(ApprovalRequestStatus.CANCELED, requestOf(pending).getStatus());

        Long approved = createApproval(admin, kim);
        approve(kim, approved);
        assertErrorCode(ApprovalErrorCode.CANCEL_NOT_ALLOWED, () -> cancel(admin, approved, "reason"));
        assertEquals(ApprovalRequestStatus.APPROVED, requestOf(approved).getStatus());
    }

    @Test
    void 다른_회사의_결재는_처리할_수_없다() {
        Long id = createApproval(requester, kim);

        assertErrorCode(ApprovalErrorCode.APPROVAL_NOT_FOUND, () ->
                approvalService.approve(otherCompany.getId(), otherAdmin.getId(), id, null));
        assertErrorCode(ApprovalErrorCode.APPROVAL_NOT_FOUND, () ->
                approvalService.reject(otherCompany.getId(), otherAdmin.getId(), id, null));
        assertErrorCode(ApprovalErrorCode.APPROVAL_NOT_FOUND, () ->
                approvalService.cancel(otherCompany.getId(), otherAdmin.getId(), id, "reason"));
        assertEquals(ApprovalRequestStatus.PENDING, requestOf(id).getStatus());

        approve(kim, id);
        assertEquals(ApprovalRequestStatus.APPROVED, requestOf(id).getStatus());
    }
}