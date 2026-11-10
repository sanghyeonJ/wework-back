package com.wework.core.leave;

import com.wework.core.approval.entity.ApprovalRequestStatus;
import com.wework.core.approval.error.ApprovalErrorCode;
import com.wework.core.approval.event.ApprovalCompletedEvent;
import com.wework.core.leave.entity.LeaveRequestStatus;
import com.wework.core.member.entity.Member;
import com.wework.global.error.BusinessException;
import com.wework.global.error.CommonErrorCode;
import com.wework.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결재 결과가 휴가 상태와 연차 장부에 반영되는 흐름 테스트.
 * 결재 처리는 API가 아니라 ApprovalService를 직접 부르고, 휴가 신청만 API로 만든다.
 * 같은 방송이 두 번 와도 한 번만 반영되는지는 이벤트를 직접 발행해서 확인한다.
 */
class LeaveApprovalFlowTest extends LeaveTestSupport {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // ===== 승인 =====

    @Test
    void 승인하면_휴가가_승인되고_사용_일수가_늘며_대기_일수는_0이_된다() throws Exception {
        grant(hong, "15");
        Long id = applyOk(hong, "ANNUAL", MONDAY, WEDNESDAY, kim);   // 3일

        approve(kim, id);

        assertThat(leaveOf(id).getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(usedDaysOf(hong)).isEqualByComparingTo("3");
        getBalance(hong)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedDays").value(3.0))
                .andExpect(jsonPath("$.remainingDays").value(12.0))
                .andExpect(jsonPath("$.pendingDays").value(0.0));
    }

    @Test
    void 결재자가_두_명이면_마지막_승인에서만_반영된다() throws Exception {
        grant(hong, "15");
        Long id = applyOk(hong, "ANNUAL", MONDAY, TUESDAY, kim, park);   // 2일

        approve(kim, id);   // 중간 단계

        assertThat(leaveOf(id).getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
        assertThat(usedDaysOf(hong)).isEqualByComparingTo("0");
        getBalance(hong).andExpect(jsonPath("$.pendingDays").value(2.0));

        approve(park, id);   // 마지막 단계

        assertThat(leaveOf(id).getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(usedDaysOf(hong)).isEqualByComparingTo("2");
        getBalance(hong).andExpect(jsonPath("$.pendingDays").value(0.0));
    }

    @Test
    void 반차는_0_5일씩_반영된다() throws Exception {
        grant(hong, "15");
        Long am = applyOk(hong, "HALF_AM", MONDAY, MONDAY, kim);
        Long pm = applyOk(hong, "HALF_PM", MONDAY, MONDAY, kim);

        approve(kim, am);
        assertThat(usedDaysOf(hong)).isEqualByComparingTo("0.5");
        getBalance(hong)
                .andExpect(jsonPath("$.remainingDays").value(14.5))
                .andExpect(jsonPath("$.pendingDays").value(0.5));

        approve(kim, pm);
        assertThat(usedDaysOf(hong)).isEqualByComparingTo("1");
        getBalance(hong)
                .andExpect(jsonPath("$.remainingDays").value(14.0))
                .andExpect(jsonPath("$.pendingDays").value(0.0));
    }

    // ===== 반려 =====

    @Test
    void 반려하면_휴가가_반려되고_장부는_그대로이며_대기_일수만_빠진다() throws Exception {
        grant(hong, "15");
        Long id = applyOk(hong, "ANNUAL", MONDAY, WEDNESDAY, kim);

        reject(kim, id);

        assertThat(leaveOf(id).getStatus()).isEqualTo(LeaveRequestStatus.REJECTED);
        assertThat(usedDaysOf(hong)).isEqualByComparingTo("0");
        getBalance(hong)
                .andExpect(jsonPath("$.remainingDays").value(15.0))
                .andExpect(jsonPath("$.pendingDays").value(0.0));
    }

    // ===== 취소 =====

    @Test
    void 대기_중_신청자가_취소하면_휴가가_취소되고_장부는_그대로다() throws Exception {
        grant(hong, "15");
        Long id = applyOk(hong, "ANNUAL", MONDAY, WEDNESDAY, kim);

        cancel(hong, id, null);

        assertThat(leaveOf(id).getStatus()).isEqualTo(LeaveRequestStatus.CANCELED);
        assertThat(usedDaysOf(hong)).isEqualByComparingTo("0");   // 깎은 적이 없으니 복구도 없다
        getBalance(hong)
                .andExpect(jsonPath("$.remainingDays").value(15.0))
                .andExpect(jsonPath("$.pendingDays").value(0.0));
    }

    @Test
    void 승인된_휴가를_기업관리자가_취소하면_사용_일수가_돌아온다() throws Exception {
        grant(hong, "15");
        Long id = applyOk(hong, "ANNUAL", MONDAY, WEDNESDAY, kim);
        approve(kim, id);
        assertThat(usedDaysOf(hong)).isEqualByComparingTo("3");

        cancel(admin, id, "관리자 취소");

        assertThat(leaveOf(id).getStatus()).isEqualTo(LeaveRequestStatus.CANCELED);
        assertThat(usedDaysOf(hong)).isEqualByComparingTo("0");
        getBalance(hong)
                .andExpect(jsonPath("$.usedDays").value(0.0))
                .andExpect(jsonPath("$.remainingDays").value(15.0));
    }

    @Test
    void 신청자는_승인된_휴가를_취소할_수_없고_장부는_그대로다() throws Exception {
        grant(hong, "15");
        Long id = applyOk(hong, "ANNUAL", MONDAY, WEDNESDAY, kim);
        approve(kim, id);

        assertErrorCode(ApprovalErrorCode.CANCEL_NOT_ALLOWED, () -> cancel(hong, id, null));

        assertThat(leaveOf(id).getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(usedDaysOf(hong)).isEqualByComparingTo("3");
    }

    // ===== 같은 방송이 두 번 올 때 =====

    @Test
    void 같은_승인_방송이_두_번_와도_사용_일수는_한_번만_늘어난다() throws Exception {
        grant(hong, "15");
        Long id = applyOk(hong, "ANNUAL", MONDAY, WEDNESDAY, kim);
        approve(kim, id);

        publish(id, ApprovalRequestStatus.APPROVED, ApprovalRequestStatus.PENDING);   // 같은 방송을 한 번 더

        assertThat(usedDaysOf(hong)).isEqualByComparingTo("3");
    }

    @Test
    void 같은_취소_방송이_두_번_와도_사용_일수는_한_번만_복구된다() throws Exception {
        grant(hong, "15");
        Long id = applyOk(hong, "ANNUAL", MONDAY, WEDNESDAY, kim);
        approve(kim, id);   // 사용 3

        // 결재 요청 자체는 승인 상태로 두고 방송만 두 번 보낸다 (리스너의 중복 방어만 확인)
        publish(id, ApprovalRequestStatus.CANCELED, ApprovalRequestStatus.APPROVED);
        publish(id, ApprovalRequestStatus.CANCELED, ApprovalRequestStatus.APPROVED);

        assertThat(usedDaysOf(hong)).isEqualByComparingTo("0");   // -3이 되면 안 된다
        assertThat(leaveOf(id).getStatus()).isEqualTo(LeaveRequestStatus.CANCELED);
    }

    // ===== 휴가가 아닌 결재, 이상 상황 =====

    @Test
    void 휴가가_아닌_결재는_연차_장부에_영향이_없다() {
        grant(hong, "15");
        Long approvalId = approvalService.createRequest(
                company.getId(), hong.getId(), "TEST", 9999L, "다른 결재", List.of(kim.getId()));

        assertDoesNotThrow(() -> approvalService.approve(company.getId(), kim.getId(), approvalId, null));

        assertThat(usedDaysOf(hong)).isEqualByComparingTo("0");
    }

    @Test
    void 장부가_사라진_이상_상황에서는_승인이_서버_오류로_실패한다() throws Exception {
        grant(hong, "15");
        Long id = applyOk(hong, "ANNUAL", MONDAY, MONDAY, kim);
        leaveBalanceRepository.deleteAll();

        assertErrorCode(CommonErrorCode.INTERNAL_SERVER_ERROR, () -> approve(kim, id));
    }

    // ===== 도우미 =====

    private void approve(Member approver, Long leaveId) {
        approvalService.approve(company.getId(), approver.getId(), approvalIdOf(leaveId), null);
    }

    private void reject(Member approver, Long leaveId) {
        approvalService.reject(company.getId(), approver.getId(), approvalIdOf(leaveId), null);
    }

    private void cancel(Member actor, Long leaveId, String reason) {
        approvalService.cancel(company.getId(), actor.getId(), approvalIdOf(leaveId), reason);
    }

    private BigDecimal usedDaysOf(Member member) {
        return leaveBalanceRepository
                .findByCompanyIdAndMemberIdAndYear(company.getId(), member.getId(), YEAR)
                .orElseThrow()
                .getUsedDays();
    }

    private void publish(Long leaveId, ApprovalRequestStatus result, ApprovalRequestStatus previousStatus) {
        eventPublisher.publishEvent(ApprovalCompletedEvent.of(
                approvalIdOf(leaveId), company.getId(), "LEAVE", leaveId, result, previousStatus));
    }

    private static void assertErrorCode(ErrorCode expected, Executable executable) {
        BusinessException e = assertThrows(BusinessException.class, executable);
        assertEquals(expected, e.getErrorCode());
    }
}