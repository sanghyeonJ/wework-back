package com.wework.attendance;

import com.wework.attendance.dto.AttendanceResponse;
import com.wework.attendance.dto.TodayAttendanceResponse;
import com.wework.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "근태")
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Operation(summary = "출근", description = "오늘 첫 출근만 기록됩니다. 이미 출근했다면 새로 만들지 않고 기존 기록을 그대로 돌려줍니다.")
    @PostMapping("/check-in")
    public ResponseEntity<AttendanceResponse> checkIn(@AuthenticationPrincipal LoginMember loginMember) {
        return ResponseEntity.ok(
                attendanceService.checkIn(loginMember.getCompanyId(), loginMember.getMemberId())
        );
    }

    @Operation(summary = "퇴근", description = "누를 때마다 퇴근 시각이 덮어써져 마지막 퇴근이 유효합니다. 오늘 출근 기록이 없으면 NOT_CHECKED_IN 입니다.")
    @PostMapping("/check-out")
    public ResponseEntity<AttendanceResponse> checkOut(@AuthenticationPrincipal LoginMember loginMember) {
        return ResponseEntity.ok(
                attendanceService.checkOut(loginMember.getCompanyId(), loginMember.getMemberId())
        );
    }
    @Operation(summary = "오늘 내 기록", description = "회사 시간대 기준 오늘 날짜와 내 기록을 돌려줍니다. 출근 전이면 attendance가 null 입니다.")
    @GetMapping("/today")
    public ResponseEntity<TodayAttendanceResponse> getToday(@AuthenticationPrincipal LoginMember loginMember) {
        return ResponseEntity.ok(
                attendanceService.getToday(loginMember.getCompanyId(), loginMember.getMemberId())
        );
    }

    @Operation(summary = "내 월별 기록", description = "선택한 연·월의 내 근태 기록을 날짜 오름차순으로 돌려줍니다.")
    @GetMapping("/me")
    public ResponseEntity<List<AttendanceResponse>> getMonthly(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(
                attendanceService.getMonthly(loginMember.getCompanyId(), loginMember.getMemberId(), year, month)
        );
    }

}
