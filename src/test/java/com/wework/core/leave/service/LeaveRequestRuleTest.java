package com.wework.core.leave.service;

import com.wework.core.approval.entity.ApprovalRequestStatus;
import com.wework.core.leave.entity.LeaveRequest;
import com.wework.core.leave.entity.LeaveType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 휴가 신청·결재 반영의 규칙 메서드 단위 테스트 (DB·스프링 없이 숫자와 종류만 넣어 확인).
 */
class LeaveRequestRuleTest {

    // ===== 휴가 종류 =====

    @Test
    void 연차와_반차는_연차를_차감하고_병가는_차감하지_않는다() {
        assertTrue(LeaveType.ANNUAL.isDeductible());
        assertTrue(LeaveType.HALF_AM.isDeductible());
        assertTrue(LeaveType.HALF_PM.isDeductible());
        assertFalse(LeaveType.SICK.isDeductible());
    }

    // ===== 겹침 허용 규칙 =====

    @Test
    void 겹치는_휴가가_없으면_어떤_신청이든_통과한다() {
        assertTrue(LeaveRequestService.isOverlapAllowed(LeaveType.ANNUAL, List.of()));
        assertTrue(LeaveRequestService.isOverlapAllowed(LeaveType.HALF_AM, List.of()));
    }

    @Test
    void 반차는_반대_반차_한_건과만_같은_날_신청할_수_있다() {
        assertTrue(LeaveRequestService.isOverlapAllowed(LeaveType.HALF_AM, List.of(leave(LeaveType.HALF_PM))));
        assertTrue(LeaveRequestService.isOverlapAllowed(LeaveType.HALF_PM, List.of(leave(LeaveType.HALF_AM))));
    }

    @Test
    void 같은_종류_반차_연차_병가와_겹치면_거절된다() {
        assertFalse(LeaveRequestService.isOverlapAllowed(LeaveType.HALF_AM, List.of(leave(LeaveType.HALF_AM))));
        assertFalse(LeaveRequestService.isOverlapAllowed(LeaveType.HALF_AM, List.of(leave(LeaveType.ANNUAL))));
        assertFalse(LeaveRequestService.isOverlapAllowed(LeaveType.HALF_AM, List.of(leave(LeaveType.SICK))));
    }

    @Test
    void 이미_두_건이_겹치면_반차도_거절된다() {
        assertFalse(LeaveRequestService.isOverlapAllowed(LeaveType.HALF_AM,
                List.of(leave(LeaveType.HALF_AM), leave(LeaveType.HALF_PM))));
    }

    @Test
    void 새_신청이_연차이면_반차와도_겹칠_수_없다() {
        assertFalse(LeaveRequestService.isOverlapAllowed(LeaveType.ANNUAL, List.of(leave(LeaveType.HALF_AM))));
    }

    @Test
    void 병가는_다른_휴가와_겹칠_수_없다() {
        assertTrue(LeaveRequestService.isOverlapAllowed(LeaveType.SICK, List.of()));

        assertFalse(LeaveRequestService.isOverlapAllowed(LeaveType.SICK, List.of(leave(LeaveType.ANNUAL))));
        assertFalse(LeaveRequestService.isOverlapAllowed(LeaveType.SICK, List.of(leave(LeaveType.HALF_AM))));
        assertFalse(LeaveRequestService.isOverlapAllowed(LeaveType.SICK, List.of(leave(LeaveType.SICK))));
    }

    // ===== 잔여 일수 판단 =====

    @Test
    void 신청_일수가_신청_가능_일수_이하이면_통과한다_같으면_통과_넘으면_거절() {
        assertTrue(LeaveRequestService.isBalanceEnough(bd("12"), bd("2"), bd("0.5")));
        assertTrue(LeaveRequestService.isBalanceEnough(bd("12"), bd("2"), bd("10")));    // 같으면 통과
        assertFalse(LeaveRequestService.isBalanceEnough(bd("12"), bd("2"), bd("10.5")));  // 0.5 넘으면 거절
        assertTrue(LeaveRequestService.isBalanceEnough(bd("3"), bd("0"), bd("3")));       // 정확히 다 씀
    }

    @Test
    void 대기_일수로_이미_찼거나_장부가_없으면_거절된다() {
        assertFalse(LeaveRequestService.isBalanceEnough(bd("3"), bd("3"), bd("0.5")));
        assertFalse(LeaveRequestService.isBalanceEnough(bd("0"), bd("0"), bd("0.5")));
    }

    @Test
    void 소수점_자리_수가_달라도_값이_같으면_같게_본다() {
        assertTrue(LeaveRequestService.isBalanceEnough(bd("12.0"), bd("2.0"), bd("10")));
        assertTrue(LeaveRequestService.isBalanceEnough(bd("12"), bd("2"), bd("10.0")));
    }

    // ===== 결재 제목 =====

    @Test
    void 결재_제목에는_종류와_기간과_일수가_들어간다() {
        assertEquals("휴가 신청 2026-03-10 ~ 2026-03-12 (3일)",
                LeaveRequestService.buildTitle(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), bd("3"), LeaveType.ANNUAL));
        assertEquals("휴가 신청 2026-03-10 ~ 2026-03-10 (0.5일)",
                LeaveRequestService.buildTitle(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10), bd("0.5"), LeaveType.HALF_AM));
        assertEquals("병가 신청 2026-03-10 ~ 2026-03-11 (2일)",
                LeaveRequestService.buildTitle(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11), bd("2"), LeaveType.SICK));
    }

    // ===== 결재 결과 반영 판단 =====

    @Test
    void 승인일_때만_연차를_깎는다() {
        assertTrue(LeaveApprovalListener.shouldUseDays(ApprovalRequestStatus.APPROVED));
        assertFalse(LeaveApprovalListener.shouldUseDays(ApprovalRequestStatus.REJECTED));
        assertFalse(LeaveApprovalListener.shouldUseDays(ApprovalRequestStatus.CANCELED));
    }

    @Test
    void 승인됐던_건이_취소될_때만_연차를_돌려준다() {
        assertTrue(LeaveApprovalListener.shouldRestoreDays(ApprovalRequestStatus.CANCELED, ApprovalRequestStatus.APPROVED));

        assertFalse(LeaveApprovalListener.shouldRestoreDays(ApprovalRequestStatus.CANCELED, ApprovalRequestStatus.PENDING));
        assertFalse(LeaveApprovalListener.shouldRestoreDays(ApprovalRequestStatus.APPROVED, ApprovalRequestStatus.PENDING));
        assertFalse(LeaveApprovalListener.shouldRestoreDays(ApprovalRequestStatus.REJECTED, ApprovalRequestStatus.PENDING));
    }

    // ===== 도우미 =====

    private static LeaveRequest leave(LeaveType type) {
        return LeaveRequest.create(null, null, type,
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), new BigDecimal("0.5"), null);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}