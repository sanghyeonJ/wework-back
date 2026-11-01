package com.wework.core.leave.controller;

import com.wework.core.leave.dto.LeaveBalanceResponse;
import com.wework.core.leave.service.LeaveBalanceService;
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

@Tag(name = "휴가")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leave")
public class LeaveController {

    private final LeaveBalanceService leaveBalanceService;

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

}
