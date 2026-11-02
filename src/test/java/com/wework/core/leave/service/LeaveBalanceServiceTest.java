package com.wework.core.leave.service;

import com.wework.core.leave.error.LeaveErrorCode;
import com.wework.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 연차 잔액 규칙 단위 테스트 (스프링 없음).
 * remainingDays·validateGrantedDays·validateYear는 package-private static이라 바로 호출한다.
 * 경계 값은 바로 앞·바로 뒤를 쌍으로 확인한다.
 */
class LeaveBalanceServiceTest {

    // ===== remainingDays =====

    @Test
    @DisplayName("잔여는 지급에서 사용을 뺀 값이다 (지급·사용을 서로 다른 값으로 두어 순서 뒤바뀜을 잡는다)")
    void remainingDays_subtracts() {
        assertThat(LeaveBalanceService.remainingDays(bd("15"), bd("3"))).isEqualByComparingTo("12");
    }

    @Test
    @DisplayName("0.5 단위도 정확히 계산한다")
    void remainingDays_halfDay() {
        assertThat(LeaveBalanceService.remainingDays(bd("10.5"), bd("0.5"))).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("지급과 사용이 같으면 잔여는 0이다")
    void remainingDays_zeroWhenAllUsed() {
        assertThat(LeaveBalanceService.remainingDays(bd("15"), bd("15"))).isEqualByComparingTo("0");
    }

    // ===== validateGrantedDays =====

    @Test
    @DisplayName("0.5 단위의 정상 값은 통과한다 (15.0처럼 소수 첫째 자리가 0이어도 통과)")
    void validateGrantedDays_allowedValues() {
        assertValidDays("0.5");
        assertValidDays("15");
        assertValidDays("15.0");
        assertValidDays("99.5");
    }

    @Test
    @DisplayName("범위 경계: 0은 통과·-0.5는 거절, 100은 통과·100.5는 거절, 99999는 거절 (0은 지급 취소 용도라 허용)")
    void validateGrantedDays_rangeBoundary() {
        assertValidDays("0");
        assertInvalidDays("-0.5");
        assertValidDays("100");
        assertInvalidDays("100.5");
        assertInvalidDays("99999");
    }

    @Test
    @DisplayName("0.5 단위가 아니면 거절한다 (10.5는 통과, 10.3·10.55·0.1은 거절)")
    void validateGrantedDays_halfUnit() {
        assertValidDays("10.5");
        assertInvalidDays("10.3");
        assertInvalidDays("10.55");
        assertInvalidDays("0.1");
    }

    // ===== validateYear =====

    @Test
    @DisplayName("연도 하한: 2015는 통과, 2014는 거절")
    void validateYear_lowerBoundary() {
        int currentYear = LocalDate.now().getYear();

        assertThatCode(() -> LeaveBalanceService.validateYear(2015, currentYear))
                .doesNotThrowAnyException();
        assertInvalidYear(2014, currentYear);
    }

    @Test
    @DisplayName("연도 상한: 올해와 내년은 통과, 내후년은 거절")
    void validateYear_upperBoundary() {
        int currentYear = LocalDate.now().getYear();

        assertThatCode(() -> LeaveBalanceService.validateYear(currentYear, currentYear))
                .doesNotThrowAnyException();
        assertThatCode(() -> LeaveBalanceService.validateYear(currentYear + 1, currentYear))
                .doesNotThrowAnyException();
        assertInvalidYear(currentYear + 2, currentYear);
    }

    // ===== 도우미 =====

    // 소수는 double이 아니라 문자열로 만든다 (new BigDecimal(10.3)은 오차가 생김)
    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void assertValidDays(String days) {
        assertThatCode(() -> LeaveBalanceService.validateGrantedDays(bd(days)))
                .doesNotThrowAnyException();
    }

    private static void assertInvalidDays(String days) {
        assertThatThrownBy(() -> LeaveBalanceService.validateGrantedDays(bd(days)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", LeaveErrorCode.INVALID_LEAVE_DAYS);
    }

    private static void assertInvalidYear(int year, int currentYear) {
        assertThatThrownBy(() -> LeaveBalanceService.validateYear(year, currentYear))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", LeaveErrorCode.INVALID_LEAVE_YEAR);
    }
}