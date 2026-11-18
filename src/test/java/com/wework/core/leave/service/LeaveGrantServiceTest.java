package com.wework.core.leave.service;

import com.wework.core.leave.entity.LeaveGrantRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// calculateServiceYears, findGrantedDays는 package-private static이라 서비스와 같은 패키지에 둔다
class LeaveGrantServiceTest {

    // ---------- 도우미 ----------

    // 순수 계산이라 회사는 필요 없어서 null을 넣는다
    private static LeaveGrantRule rule(int serviceYear, String days) {
        return LeaveGrantRule.create(null, serviceYear, new BigDecimal(days));
    }

    private static void assertDays(String expected, Optional<BigDecimal> actual) {
        assertTrue(actual.isPresent(), "지급 일수가 있어야 한다");
        assertEquals(0, new BigDecimal(expected).compareTo(actual.get()));
    }

    private static final List<LeaveGrantRule> TABLE =
            List.of(rule(1, "15"), rule(3, "16"), rule(5, "17"));

    // ---------- 근속연수 계산 ----------

    @Test
    @DisplayName("근속연수는 입사일의 월·일과 상관없이 연도 차이만 센다")
    void serviceYearsIsYearDifference() {
        assertEquals(6, LeaveGrantService.calculateServiceYears(LocalDate.of(2020, 1, 1), 2026));
        assertEquals(6, LeaveGrantService.calculateServiceYears(LocalDate.of(2020, 3, 2), 2026));
        assertEquals(6, LeaveGrantService.calculateServiceYears(LocalDate.of(2020, 12, 31), 2026));
    }

    @Test
    @DisplayName("입사 첫해는 0년차, 바로 전 해 12월 입사는 1년차 (경계 쌍)")
    void firstYearIsZero() {
        assertEquals(0, LeaveGrantService.calculateServiceYears(LocalDate.of(2026, 1, 1), 2026));
        assertEquals(0, LeaveGrantService.calculateServiceYears(LocalDate.of(2026, 12, 31), 2026));
        assertEquals(1, LeaveGrantService.calculateServiceYears(LocalDate.of(2025, 12, 31), 2026));
    }

    // ---------- 지급 일수 고르기 ----------

    @Test
    @DisplayName("0년차 이하는 지급 안 함, 1년차부터 지급 (경계 쌍)")
    void belowOneYearGetsNothing() {
        assertTrue(LeaveGrantService.findGrantedDays(TABLE, 0).isEmpty());
        assertTrue(LeaveGrantService.findGrantedDays(TABLE, -1).isEmpty());
        assertDays("15", LeaveGrantService.findGrantedDays(TABLE, 1));
    }

    @Test
    @DisplayName("근속연수 '이상'인 줄 중 가장 큰 줄을 쓴다 (줄 사이 구간 포함)")
    void usesClosestLowerRule() {
        assertDays("15", LeaveGrantService.findGrantedDays(TABLE, 2));
        assertDays("16", LeaveGrantService.findGrantedDays(TABLE, 3));
        assertDays("16", LeaveGrantService.findGrantedDays(TABLE, 4));
        assertDays("17", LeaveGrantService.findGrantedDays(TABLE, 5));
        assertDays("17", LeaveGrantService.findGrantedDays(TABLE, 30));
    }

    @Test
    @DisplayName("줄이 섞인 순서로 와도 결과가 같다")
    void orderOfRulesDoesNotMatter() {
        List<LeaveGrantRule> shuffled = List.of(rule(5, "17"), rule(1, "15"), rule(3, "16"));
        assertDays("16", LeaveGrantService.findGrantedDays(shuffled, 4));
        assertDays("17", LeaveGrantService.findGrantedDays(shuffled, 5));
    }

    @Test
    @DisplayName("설정 표가 비어 있으면 지급 안 함")
    void emptyRulesGetNothing() {
        assertTrue(LeaveGrantService.findGrantedDays(List.of(), 3).isEmpty());
    }

    @Test
    @DisplayName("내 근속연수 이하의 줄이 없으면 지급 안 함, 딱 맞으면 지급 (경계 쌍)")
    void noRuleAtOrBelowServiceYears() {
        List<LeaveGrantRule> startsAtThree = List.of(rule(3, "16"));
        assertTrue(LeaveGrantService.findGrantedDays(startsAtThree, 2).isEmpty());
        assertDays("16", LeaveGrantService.findGrantedDays(startsAtThree, 3));
    }

    @Test
    @DisplayName("지급 일수가 0인 줄은 '지급 안 함'이 아니라 0일로 돌려준다")
    void zeroDaysRuleIsStillPresent() {
        assertDays("0", LeaveGrantService.findGrantedDays(List.of(rule(1, "0")), 1));
    }
}