package com.wework.attendance.service;

import com.wework.attendance.entity.WorkScheduleSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 스프링을 띄우지 않는 단위 테스트 (IntegrationTestSupport를 상속하지 않음)
@DisplayName("지각·조퇴 판단")
class AttendanceJudgementTest {

    // ===== 지각 =====

    @Test
    @DisplayName("지각: 기준 10:00 + 유예 10분 → 10:10:59까지 정상, 10:11:00부터 지각")
    void lateBoundaryWithGrace() {
        EffectiveWorkSchedule schedule = schedule(10, 0, 19, 0, 10);

        assertFalse(AttendanceService.isLate(LocalTime.of(10, 0, 0), schedule));
        assertFalse(AttendanceService.isLate(LocalTime.of(10, 10, 0), schedule));
        assertFalse(AttendanceService.isLate(LocalTime.of(10, 10, 59), schedule)); // 초는 버림
        assertTrue(AttendanceService.isLate(LocalTime.of(10, 11, 0), schedule));
    }

    @Test
    @DisplayName("지각: 유예 0분 → 09:00:59까지 정상, 09:01:00부터 지각")
    void lateBoundaryWithoutGrace() {
        EffectiveWorkSchedule schedule = schedule(9, 0, 18, 0, 0);

        assertFalse(AttendanceService.isLate(LocalTime.of(9, 0, 0), schedule));
        assertFalse(AttendanceService.isLate(LocalTime.of(9, 0, 59), schedule));
        assertTrue(AttendanceService.isLate(LocalTime.of(9, 1, 0), schedule));
    }

    @Test
    @DisplayName("기준보다 일찍 출근하면 지각이 아니다")
    void earlyArrivalIsNotLate() {
        EffectiveWorkSchedule schedule = schedule(9, 0, 18, 0, 0);

        assertFalse(AttendanceService.isLate(LocalTime.of(0, 0, 0), schedule));
        assertFalse(AttendanceService.isLate(LocalTime.of(8, 30, 0), schedule));
    }

    @Test
    @DisplayName("버그 방지: 기준 시각 + 유예 분이 자정을 넘어도 늦은 밤 출근이 전부 지각이 되지 않는다")
    void graceDoesNotOverflowMidnight() {
        // 22:00 + 180분 = 25:00 (자정 넘김). plusMinutes로 더하면 01:00이 되어
        // 22:00 이후 출근이 전부 "01:00보다 늦음 = 지각"이 되는 버그가 생긴다.
        EffectiveWorkSchedule schedule = schedule(22, 0, 23, 59, 180);

        assertFalse(AttendanceService.isLate(LocalTime.of(22, 30, 0), schedule));
        assertFalse(AttendanceService.isLate(LocalTime.of(23, 59, 59), schedule));
    }

    // ===== 조퇴 =====

    @Test
    @DisplayName("조퇴: 기준 18:00 → 17:59:xx는 조퇴, 18:00:00은 정상")
    void earlyLeaveBoundary() {
        EffectiveWorkSchedule schedule = schedule(9, 0, 18, 0, 0);

        assertTrue(AttendanceService.isEarlyLeave(LocalTime.of(17, 59, 0), schedule));
        assertTrue(AttendanceService.isEarlyLeave(LocalTime.of(17, 59, 59), schedule)); // 화면에 17:59로 보이므로 조퇴
        assertFalse(AttendanceService.isEarlyLeave(LocalTime.of(18, 0, 0), schedule));
    }

    @Test
    @DisplayName("기준 시각 이후 퇴근은 조퇴가 아니다")
    void onTimeAndOvertimeAreNotEarlyLeave() {
        EffectiveWorkSchedule schedule = schedule(9, 0, 18, 0, 0);

        assertFalse(AttendanceService.isEarlyLeave(LocalTime.of(18, 0, 59), schedule));
        assertFalse(AttendanceService.isEarlyLeave(LocalTime.of(18, 1, 0), schedule));
        assertFalse(AttendanceService.isEarlyLeave(LocalTime.of(23, 59, 59), schedule));
    }

    @Test
    @DisplayName("조퇴 판단에는 지각 유예 분이 영향을 주지 않는다")
    void graceDoesNotAffectEarlyLeave() {
        EffectiveWorkSchedule schedule = schedule(9, 0, 18, 0, 30);

        assertTrue(AttendanceService.isEarlyLeave(LocalTime.of(17, 59, 0), schedule));
        assertFalse(AttendanceService.isEarlyLeave(LocalTime.of(18, 0, 0), schedule));
    }

    // ===== 도우미 =====

    private static EffectiveWorkSchedule schedule(int startHour, int startMinute, int endHour, int endMinute, int graceMinutes) {
        return EffectiveWorkSchedule.of(WorkScheduleSource.COMPANY,
                LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute), graceMinutes);
    }
}