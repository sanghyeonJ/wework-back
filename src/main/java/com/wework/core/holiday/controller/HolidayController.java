package com.wework.core.holiday.controller;

import com.wework.core.holiday.dto.HolidayResponse;
import com.wework.core.holiday.service.HolidayCalendarService;
import com.wework.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "휴일")
@RestController
@RequestMapping("/api/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayCalendarService holidayCalendarService;

    @Operation(
            summary = "휴일 조회",
            description = "내 회사의 국가 공휴일과 회사 휴일을 합쳐 날짜순으로 조회합니다. "
                    + "type이 PUBLIC이면 국가 공휴일, COMPANY이면 회사 휴일입니다. "
                    + "회사 휴일 중 반복 휴일은 조회한 해의 날짜로 바꿔서 내려갑니다. "
                    + "한국 외 국가의 회사는 국가 공휴일 없이 회사 휴일만 나옵니다."
    )
    @GetMapping
    public ResponseEntity<List<HolidayResponse>> getHolidays(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestParam int year
            ) {
        Long companyId = loginMember.getCompanyId();
        return ResponseEntity.ok(holidayCalendarService.getYearHolidays(companyId, year));
    }

}
