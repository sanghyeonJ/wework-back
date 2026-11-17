package com.wework.core.leave.service;

import com.wework.core.leave.dto.LeaveGrantRuleRequest;
import com.wework.core.leave.error.LeaveErrorCode;
import com.wework.global.error.BusinessException;
import com.wework.global.error.CommonErrorCode;
import com.wework.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// validateRules는 package-private static이라 서비스와 같은 패키지에 둔다
class LeaveGrantRuleServiceTest {

    // ---------- 도우미 ----------

    // Item에는 setter가 없어서 ReflectionTestUtils로 값을 넣는다
    private static LeaveGrantRuleRequest.Item item(int serviceYear, String grantedDays) {
        LeaveGrantRuleRequest.Item item = new LeaveGrantRuleRequest.Item();
        ReflectionTestUtils.setField(item, "serviceYear", serviceYear);
        ReflectionTestUtils.setField(item, "grantedDays", new BigDecimal(grantedDays));
        return item;
    }

    // 에러 코드를 꺼내는 방법은 이 한 곳에만 있다
    private static void assertError(ErrorCode expected, List<LeaveGrantRuleRequest.Item> items) {
        BusinessException e = assertThrows(BusinessException.class,
                () -> LeaveGrantRuleService.validateRules(items));
        assertEquals(expected, e.getErrorCode());
    }

    // ---------- 통과 ----------

    @Test
    @DisplayName("빈 목록은 통과한다 (설정 해제)")
    void emptyListPasses() {
        assertDoesNotThrow(() -> LeaveGrantRuleService.validateRules(List.of()));
    }

    @Test
    @DisplayName("정상적인 표는 통과한다")
    void validRulesPass() {
        assertDoesNotThrow(() -> LeaveGrantRuleService.validateRules(
                List.of(item(1, "15"), item(3, "16"))));
    }

    // ---------- null 항목 ----------

    @Test
    @DisplayName("null 항목은 INVALID_INPUT")
    void nullItem() {
        List<LeaveGrantRuleRequest.Item> items = Collections.singletonList(null);
        assertError(CommonErrorCode.INVALID_INPUT, items);
    }

    // ---------- 근속연수 경계 (0/1, 50/51) ----------

    @Test
    @DisplayName("근속연수 0은 거절 (최소값 바로 아래)")
    void serviceYearBelowMin() {
        assertError(LeaveErrorCode.INVALID_SERVICE_YEAR, List.of(item(0, "15")));
    }

    @Test
    @DisplayName("근속연수 1은 통과 (최소값)")
    void serviceYearMin() {
        assertDoesNotThrow(() -> LeaveGrantRuleService.validateRules(List.of(item(1, "15"))));
    }

    @Test
    @DisplayName("근속연수 50은 통과 (최대값)")
    void serviceYearMax() {
        assertDoesNotThrow(() -> LeaveGrantRuleService.validateRules(List.of(item(50, "25"))));
    }

    @Test
    @DisplayName("근속연수 51은 거절 (최대값 바로 위)")
    void serviceYearAboveMax() {
        assertError(LeaveErrorCode.INVALID_SERVICE_YEAR, List.of(item(51, "25")));
    }

    // ---------- 중복 ----------

    @Test
    @DisplayName("같은 근속연수가 두 번 나오면 DUPLICATE_SERVICE_YEAR (사이에 다른 값이 있어도)")
    void duplicateServiceYear() {
        assertError(LeaveErrorCode.DUPLICATE_SERVICE_YEAR,
                List.of(item(1, "15"), item(3, "16"), item(1, "17")));
    }

    // ---------- 지급 일수 (기존 validateGrantedDays 재사용) ----------

    @Test
    @DisplayName("지급 일수 0과 100은 통과 (경계)")
    void grantedDaysBoundaryPass() {
        assertDoesNotThrow(() -> LeaveGrantRuleService.validateRules(
                List.of(item(1, "0"), item(2, "100"))));
    }

    @Test
    @DisplayName("지급 일수 100.5는 INVALID_LEAVE_DAYS")
    void grantedDaysAboveMax() {
        assertError(LeaveErrorCode.INVALID_LEAVE_DAYS, List.of(item(1, "100.5")));
    }

    @Test
    @DisplayName("지급 일수 10.3은 0.5 단위가 아니라 INVALID_LEAVE_DAYS")
    void grantedDaysNotHalfUnit() {
        assertError(LeaveErrorCode.INVALID_LEAVE_DAYS, List.of(item(1, "10.3")));
    }

    // ---------- 검사 순서 ----------

    @Test
    @DisplayName("근속연수 범위 검사가 지급 일수 검사보다 먼저")
    void serviceYearCheckedBeforeDays() {
        assertError(LeaveErrorCode.INVALID_SERVICE_YEAR, List.of(item(0, "200")));
    }

    @Test
    @DisplayName("중복 검사가 지급 일수 검사보다 먼저")
    void duplicateCheckedBeforeDays() {
        assertError(LeaveErrorCode.DUPLICATE_SERVICE_YEAR, List.of(item(1, "15"), item(1, "200")));
    }
}