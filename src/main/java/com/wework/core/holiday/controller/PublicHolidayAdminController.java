package com.wework.core.holiday.controller;

import com.wework.core.holiday.dto.PublicHolidayResponse;
import com.wework.core.holiday.dto.PublicHolidaySyncResponse;
import com.wework.core.holiday.service.PublicHolidayService;
import com.wework.core.holiday.service.PublicHolidaySyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "국가 공휴일")
@RestController
@RequestMapping("/api/admin/public-holidays")
@RequiredArgsConstructor
public class PublicHolidayAdminController {

    private final PublicHolidaySyncService publicHolidaySyncService;
    private final PublicHolidayService publicHolidayService;

    @Operation(
            summary = "국가 공휴일 조회",
            description = "저장된 국가 공휴일을 날짜순으로 조회합니다. 저장된 것이 없으면 빈 목록입니다."
    )
    @GetMapping
    public ResponseEntity<List<PublicHolidayResponse>> getHolidays(
            @RequestParam String countryCode,
            @RequestParam int year
    ) {
        return ResponseEntity.ok(publicHolidayService.getHolidays(countryCode, year));
    }

    @Operation(
            summary = "국가 공휴일 불러오기",
            description = "공공데이터포털(한국천문연구원 특일 정보)에서 해당 연도의 공휴일을 불러와 저장합니다. "
                    + "한국(KR)만 지원하며 다른 국가는 400(UNSUPPORTED_HOLIDAY_COUNTRY)입니다. "
                    + "연도는 2015년부터 내년까지이고 범위 밖이면 400(INVALID_HOLIDAY_YEAR)입니다. "
                    + "이미 저장된 공휴일은 건드리지 않고 새로운 것만 추가하므로 여러 번 호출해도 결과가 같습니다. "
                    + "공공 API 호출에 실패하면 502(HOLIDAY_API_FAILED)이고, 이때는 아무것도 저장되지 않습니다."
    )
    @PostMapping("/sync")
    public ResponseEntity<PublicHolidaySyncResponse> sync(
            @RequestParam String countryCode,
            @RequestParam int year
    ) {
        return ResponseEntity.ok(publicHolidaySyncService.sync(countryCode, year));
    }

}
