package com.wework.core.leave.controller;

import com.wework.core.leave.dto.LeaveApplyRequest;
import com.wework.core.leave.dto.LeaveApplyResponse;
import com.wework.core.leave.dto.LeaveBalanceResponse;
import com.wework.core.leave.dto.LeaveDaysResponse;
import com.wework.core.leave.entity.LeaveType;
import com.wework.core.leave.service.LeaveBalanceService;
import com.wework.core.leave.service.LeaveDayService;
import com.wework.core.leave.service.LeaveRequestService;
import com.wework.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "휴가")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leave")
public class LeaveController {

    private final LeaveBalanceService leaveBalanceService;
    private final LeaveDayService leaveDayService;
    private final LeaveRequestService leaveRequestService;

    @Operation(
            summary = "내 연차 잔액 조회",
            description = "로그인한 사원의 해당 연도 연차 잔액(지급·사용·잔여)을 조회합니다. "
                    + "year는 필수이며 없으면 400입니다. "
                    + "지급 기록이 없는 해는 에러가 아니라 지급·사용·잔여 모두 0일로 응답합니다. "
                    + "최고관리자는 회사가 없어 403입니다."
    )
    @GetMapping("/balance")
    public ResponseEntity<LeaveBalanceResponse> getMyBalance(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestParam int year
            ) {
        Long companyId = loginMember.getCompanyId();
        Long memberId = loginMember.getMemberId();
        return ResponseEntity.ok(leaveBalanceService.getMyBalance(companyId, memberId, year));
    }

    @Operation(
            summary = "연차 일수 확인",
            description = "연차·병가는 주말, 국가 공휴일, 회사 휴일을 뺀 일수입니다. "
                    + "반차는 근무일이면 0.5일, 쉬는 날이면 0일입니다. "
                    + "결과가 0일이어도 에러가 아닙니다. "
                    + "leaveType, startDate, endDate는 필수이며 없으면 400입니다. "
                    + "시작일이 종료일보다 늦거나, 연도가 걸치거나, 반차가 하루가 아니면 INVALID_LEAVE_PERIOD(400)입니다. "
                    + "최고관리자는 회사가 없어 403입니다."
    )
    @GetMapping("/days")
    public ResponseEntity<LeaveDaysResponse> getDays(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestParam LeaveType leaveType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)LocalDate endDate
            ) {
        Long companyId = loginMember.getCompanyId();

        return ResponseEntity.ok(LeaveDaysResponse.of(leaveDayService.calculateDays(companyId, leaveType, startDate, endDate)));
    }

    @Operation(
            summary = "휴가 신청",
            description = "연차·반차를 신청하고 결재자를 1~5명 지정합니다(순서가 결재 순서). "
                    + "신청 일수는 서버가 계산하며, 과거 날짜도 신청할 수 있습니다. "
                    + "병가는 아직 지원하지 않아 LEAVE_TYPE_NOT_SUPPORTED(400)입니다. "
                    + "기간이 올바르지 않으면 INVALID_LEAVE_PERIOD(400), 근무일이 없으면(쉬는 날 반차 포함) NO_WORKING_DAY(400)입니다. "
                    + "결재자가 본인이거나 중복이거나 퇴사자이면 INVALID_APPROVER(400), 다른 회사·없는 사원이면 404입니다. "
                    + "기존 신청과 기간이 겹치면(오전·오후 반차 조합은 허용) LEAVE_OVERLAPPED(409), "
                    + "잔여 연차에서 승인 대기 일수를 뺀 값이 부족하면 INSUFFICIENT_LEAVE_BALANCE(409)입니다. "
                    + "퇴사자는 MEMBER_RESIGNED(409)입니다. 성공하면 201과 만들어진 신청의 id를 돌려줍니다. "
                    + "최고관리자는 회사가 없어 403입니다."
    )
    @PostMapping("/requests")
    public ResponseEntity<LeaveApplyResponse> apply(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody LeaveApplyRequest request
            ) {
        Long companyId = loginMember.getCompanyId();
        Long memberId = loginMember.getMemberId();
        Long id = leaveRequestService.apply(companyId, memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(LeaveApplyResponse.of(id));
    }

}
