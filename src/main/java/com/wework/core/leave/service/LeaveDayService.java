package com.wework.core.leave.service;

import com.wework.core.holiday.dto.HolidayResponse;
import com.wework.core.holiday.service.HolidayCalendarService;
import com.wework.core.leave.entity.LeaveType;
import com.wework.core.leave.error.LeaveErrorCode;
import com.wework.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 휴가 일수 계산.
 * 신청 화면의 "총 N일" 표시(GET /api/leave/days)와 휴가 신청 저장(3-4)이
 * 같은 계산을 쓰도록 이 서비스 한 곳에 모은다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveDayService {

    private final HolidayCalendarService holidayCalendarService;

    /**
     * 시작일~종료일(종료일 당일 포함)에서 주말과 쉬는 날을 뺀 일수를 센다.
     * 쉬는 날은 직접 찾지 않고 밖에서 받는다 (DB를 몰라서 스프링 없이 단위 테스트 가능).
     * 주말 판단은 HolidayCalendarService.isWeekend 한 곳을 같이 쓴다 (근태 조회와 규칙이 갈라지지 않게).
     * 기간이 말이 되는지는 검사하지 않는다 (validatePeriod가 먼저 검사해야 하며,
     * 그 덕분에 반복은 최대 366번).
     */
    static int countWorkDays(LocalDate from, LocalDate to, Set<LocalDate> holidayDates) {
        int count = 0;
        LocalDate date = from;

        while (!date.isAfter(to)){
            if (!HolidayCalendarService.isWeekend(date) && !holidayDates.contains(date)) {
                count++;
            }
            date = date.plusDays(1);
        }
        return count;
    }

    /**
     * 휴가 기간이 말이 되는지 검사한다. 하나라도 걸리면 INVALID_LEAVE_PERIOD(400).
     * - 시작일이 종료일보다 늦음
     * - 연도가 바뀜 (연차는 연도별로 관리하므로 연도를 걸친 신청은 금지)
     * - 반차인데 하루가 아님
     * 순서 주의: 시작일 > 종료일을 먼저 걸러야 아래 검사들이 의미가 있다.
     */
    static void validatePeriod(LeaveType leaveType, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(LeaveErrorCode.INVALID_LEAVE_PERIOD);
        }
        if (from.getYear() != to.getYear()) {
            throw new BusinessException(LeaveErrorCode.INVALID_LEAVE_PERIOD);
        }
        if (leaveType.isHalfDay() && !from.equals(to)) {
            throw new BusinessException(LeaveErrorCode.INVALID_LEAVE_PERIOD);
        }
    }

    /**
     * 휴가 신청 기간의 차감 일수를 계산한다 (컨트롤러와 휴가 신청이 함께 사용).
     * - 반차는 근무일이면 0.5일, 쉬는 날(주말·공휴일·회사 휴일)이면 0일
     * - 연차·병가는 주말 + 국가 공휴일 + 회사 휴일을 뺀 일수 (병가 규칙은 3-5에서 확정)
     * - 결과가 0일이어도 에러를 내지 않는다 (0일이면 거절하는 것은 신청 단계의 규칙)
     * companyId는 토큰에서 꺼낸 값을 받아, 내 회사의 휴일만 반영한다.
     */
    public BigDecimal calculateDays(Long companyId, LeaveType leaveType, LocalDate from, LocalDate to) {
        validatePeriod(leaveType, from, to);

        List<HolidayResponse> holidays = holidayCalendarService.getHolidays(companyId, from, to);
        Set<LocalDate> holidayDates = holidays.stream()
                .map(HolidayResponse::getDate)
                .collect(Collectors.toSet());
        int workDays = countWorkDays(from, to, holidayDates);

        if (leaveType.isHalfDay() && workDays > 0) {
            return BigDecimal.valueOf(0.5);
        }
        return BigDecimal.valueOf(workDays);
    }

}
