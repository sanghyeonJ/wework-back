package com.wework.core.holiday.controller;

import com.wework.core.holiday.dto.CompanyHolidayRequest;
import com.wework.core.holiday.dto.CompanyHolidayResponse;
import com.wework.core.holiday.service.CompanyHolidayService;
import com.wework.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "회사 휴일")
@RestController
@RequestMapping("/api/company/holidays")
@RequiredArgsConstructor
public class CompanyHolidayController {

    private final CompanyHolidayService companyHolidayService;

    @Operation(
            summary = "회사 휴일 목록 조회",
            description = "해당 연도의 일회성 휴일과 매년 반복되는 휴일을 함께 조회합니다. "
                    + "반복 휴일은 등록한 날짜 그대로 내려가며 recurring이 true로 표시됩니다."
    )
    @GetMapping
    public ResponseEntity<List<CompanyHolidayResponse>> getHolidays(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestParam int year
            ) {
        Long companyId = loginMember.getCompanyId();

        return ResponseEntity.ok(companyHolidayService.getHolidays(companyId, year));
    }

    @Operation(
            summary = "회사 휴일 등록",
            description = "회사 휴일(창립기념일 등)을 등록합니다. recurring이 true면 해마다 반복됩니다. "
                    + "같은 회사에 같은 날짜가 이미 등록되어 있으면 409(HOLIDAY_DATE_DUPLICATED)입니다."
    )
    @PostMapping
    public ResponseEntity<CompanyHolidayResponse> createHoliday(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestBody @Valid CompanyHolidayRequest request
            ) {
        Long companyId = loginMember.getCompanyId();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyHolidayService.create(companyId, request));
    }

    @Operation(
            summary = "회사 휴일 수정",
            description = "회사 휴일의 날짜·이름·반복 여부를 수정합니다. 세 값을 항상 함께 보냅니다. "
                    + "없는 휴일이거나 다른 회사의 휴일이면 404(HOLIDAY_NOT_FOUND), "
                    + "날짜가 이 회사의 다른 휴일과 겹치면 409(HOLIDAY_DATE_DUPLICATED)입니다."
    )
    @PatchMapping("/{id}")
    public ResponseEntity<CompanyHolidayResponse> updateHoliday(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestBody @Valid CompanyHolidayRequest request,
            @PathVariable Long id
    ) {
        Long companyId = loginMember.getCompanyId();

        return ResponseEntity.ok(companyHolidayService.update(companyId, id, request));
    }

    @Operation(
            summary = "회사 휴일 삭제",
            description = "회사 휴일을 삭제합니다(실제 삭제). "
                    + "없는 휴일이거나 다른 회사의 휴일이면 404(HOLIDAY_NOT_FOUND)입니다."
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHoliday(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long id
    ) {
        Long companyId = loginMember.getCompanyId();
        companyHolidayService.delete(companyId, id);

        return ResponseEntity.noContent().build();
    }

}
