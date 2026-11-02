package com.wework.core.leave.service;

import com.wework.core.leave.entity.LeaveType;
import com.wework.core.leave.error.LeaveErrorCode;
import com.wework.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 휴가 일수 계산 단위 테스트 (스프링 없음).
 * countWorkDays·validatePeriod는 DB를 안 쓰는 package-private static이라 바로 호출한다.
 * 요일은 고정 날짜 대신 "올해 3월의 첫 월요일"에서 계산해, 해가 바뀌어도 같은 요일 구성이 되게 한다.
 */
class LeaveDayServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(LocalDate.now().getYear(), 3, 1)
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);
    private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
    private static final LocalDate FRIDAY = MONDAY.plusDays(4);
    private static final LocalDate SATURDAY = MONDAY.plusDays(5);
    private static final LocalDate SUNDAY = MONDAY.plusDays(6);
    private static final LocalDate NEXT_MONDAY = MONDAY.plusDays(7);

    // ===== countWorkDays =====

    @Test
    @DisplayName("월~금은 종료일 당일까지 포함해 5일이다")
    void countWorkDays_weekdays() {
        assertThat(LeaveDayService.countWorkDays(MONDAY, FRIDAY, Set.of())).isEqualTo(5);
    }

    @Test
    @DisplayName("하루짜리는 금 1, 토 0, 일 0, 월 1이다 (경계 쌍, 하루 연차가 무한 반복·0일이 되는 버그 방지)")
    void countWorkDays_singleDay_boundary() {
        assertThat(LeaveDayService.countWorkDays(FRIDAY, FRIDAY, Set.of())).isEqualTo(1);
        assertThat(LeaveDayService.countWorkDays(SATURDAY, SATURDAY, Set.of())).isEqualTo(0);
        assertThat(LeaveDayService.countWorkDays(SUNDAY, SUNDAY, Set.of())).isEqualTo(0);
        assertThat(LeaveDayService.countWorkDays(NEXT_MONDAY, NEXT_MONDAY, Set.of())).isEqualTo(1);
    }

    @Test
    @DisplayName("금~다음 주 월은 주말을 빼고 2일이다")
    void countWorkDays_acrossWeekend() {
        assertThat(LeaveDayService.countWorkDays(FRIDAY, NEXT_MONDAY, Set.of())).isEqualTo(2);
    }

    @Test
    @DisplayName("쉬는 날(수요일)이 끼면 그날을 뺀 4일이다")
    void countWorkDays_excludesHoliday() {
        assertThat(LeaveDayService.countWorkDays(MONDAY, FRIDAY, Set.of(WEDNESDAY))).isEqualTo(4);
    }

    @Test
    @DisplayName("시작일(월)과 종료일(금)이 쉬는 날이면 둘 다 빠져 3일이다")
    void countWorkDays_holidayOnStartAndEnd() {
        assertThat(LeaveDayService.countWorkDays(MONDAY, FRIDAY, Set.of(MONDAY, FRIDAY))).isEqualTo(3);
    }

    @Test
    @DisplayName("쉬는 날이 주말과 겹쳐도 평일 수는 그대로 5일이다")
    void countWorkDays_holidayOnWeekend() {
        assertThat(LeaveDayService.countWorkDays(MONDAY, SUNDAY, Set.of(SATURDAY))).isEqualTo(5);
    }

    @Test
    @DisplayName("기간 밖의 쉬는 날은 결과에 영향이 없다")
    void countWorkDays_ignoresHolidayOutsideRange() {
        assertThat(LeaveDayService.countWorkDays(MONDAY, FRIDAY, Set.of(NEXT_MONDAY))).isEqualTo(5);
    }

    // ===== validatePeriod =====

    @Test
    @DisplayName("연차 월~금은 통과한다")
    void validatePeriod_normal() {
        assertValid(LeaveType.ANNUAL, MONDAY, FRIDAY);
    }

    @Test
    @DisplayName("시작일이 종료일보다 하루 늦으면 거절, 같은 날이면 통과 (경계 쌍)")
    void validatePeriod_startAfterEnd() {
        assertInvalid(LeaveType.ANNUAL, TUESDAY, MONDAY);
        assertValid(LeaveType.ANNUAL, MONDAY, MONDAY);
    }

    @Test
    @DisplayName("연도가 걸치면(12/31~1/1) 거절, 같은 해(12/30~12/31)는 통과 (경계 쌍)")
    void validatePeriod_crossYear() {
        int year = MONDAY.getYear();
        assertInvalid(LeaveType.ANNUAL, LocalDate.of(year, 12, 31), LocalDate.of(year + 1, 1, 1));
        assertValid(LeaveType.ANNUAL, LocalDate.of(year, 12, 30), LocalDate.of(year, 12, 31));
    }

    @Test
    @DisplayName("반차는 하루만 가능하고(오전·오후 모두), 연차는 이틀도 가능하다")
    void validatePeriod_halfDay() {
        assertValid(LeaveType.HALF_AM, MONDAY, MONDAY);
        assertInvalid(LeaveType.HALF_AM, MONDAY, TUESDAY);
        assertInvalid(LeaveType.HALF_PM, MONDAY, TUESDAY);
        assertValid(LeaveType.ANNUAL, MONDAY, TUESDAY);
    }

    // ===== 도우미 =====

    private static void assertValid(LeaveType type, LocalDate from, LocalDate to) {
        assertThatCode(() -> LeaveDayService.validatePeriod(type, from, to))
                .doesNotThrowAnyException();
    }

    private static void assertInvalid(LeaveType type, LocalDate from, LocalDate to) {
        assertThatThrownBy(() -> LeaveDayService.validatePeriod(type, from, to))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", LeaveErrorCode.INVALID_LEAVE_PERIOD);
    }
}