package com.wework.core.leave.controller;

import com.wework.core.leave.dto.LeaveBalanceRequest;
import com.wework.core.leave.dto.LeaveBalanceResponse;
import com.wework.core.leave.service.LeaveBalanceService;
import com.wework.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "휴가 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leave/manage")
public class LeaveManageController {

    private final LeaveBalanceService leaveBalanceService;

    @Operation(
            summary = "연차 지급 일수 입력",
            description = "해당 사원·연도의 장부가 없으면 새로 만들고, 있으면 지급 일수를 바꿉니다. " +
                    "지급 일수는 0 이상 100 이하, 0.5 단위입니다 (INVALID_LEAVE_DAYS). " +
                    "연도는 2015 ~ 올해+1입니다 (INVALID_LEAVE_YEAR). " +
                    "이미 쓴 일수보다 작게 낮출 수 없습니다 (GRANTED_DAYS_BELOW_USED, 409). " +
                    "퇴사자는 MEMBER_RESIGNED(409), 다른 회사·없는 사원은 MEMBER_NOT_FOUND(404)입니다."
    )
    @PutMapping("/balances/{memberId}/{year}")
    public ResponseEntity<LeaveBalanceResponse> saveBalance(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long memberId,
            @PathVariable int year,
            @Valid @RequestBody LeaveBalanceRequest request
            ) {
        Long companyId = loginMember.getCompanyId();
        BigDecimal grantedDays = request.getGrantedDays();
        return ResponseEntity.ok(
                leaveBalanceService.saveBalance(companyId, memberId, year, grantedDays)
        );
    }

}
