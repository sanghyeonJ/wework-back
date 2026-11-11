package com.wework.core.leave.controller;

import com.wework.core.leave.dto.LeaveBalanceMemberResponse;
import com.wework.core.leave.dto.LeaveBalanceRequest;
import com.wework.core.leave.dto.LeaveBalanceResponse;
import com.wework.core.leave.dto.LeaveManageResponse;
import com.wework.core.leave.entity.LeaveRequestStatus;
import com.wework.core.leave.service.LeaveBalanceService;
import com.wework.core.leave.service.LeaveRequestService;
import com.wework.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    private final LeaveRequestService leaveRequestService;

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

    @Operation(
            summary = "연차 현황 목록 조회",
            description = "오늘 기준 재직 중인 사원을 이름순으로 한 페이지씩 보여 주고, "
                    + "사원마다 해당 연도의 연차 장부(지급·사용·잔여)를 붙입니다. "
                    + "해당 연도의 장부가 없는 사원은 balance가 null이며 \"미지급\"을 뜻합니다. "
                    + "year는 필수이며 없으면 400입니다. "
                    + "size는 기본 20, 최대 100이고 sort 값은 무시됩니다. "
                    + "범위를 넘은 페이지는 에러 없이 빈 content를 돌려줍니다. "
                    + "부서 필터는 없습니다."
    )
    @GetMapping("/balances")
    public ResponseEntity<Page<LeaveBalanceMemberResponse>> listBalances(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestParam int year,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
            ) {
        Long companyId = loginMember.getCompanyId();

        return ResponseEntity.ok(
                leaveBalanceService.listBalances(companyId, year, pageable)
        );
    }


    @Operation(
            summary = "휴가 신청 목록 조회",
            description = "회사 전체의 휴가 신청을 시작일 최신순으로 조회합니다. 연도는 시작일 기준입니다. "
                    + "year는 필수이며 없으면 400, 범위를 벗어나면 INVALID_LEAVE_YEAR(400)입니다. "
                    + "status는 선택이며 없으면 전체입니다. "
                    + "memberId는 선택이며 없으면 회사 전체이고, 다른 회사·없는 사원이면 빈 목록입니다. "
                    + "퇴사한 사원의 신청도 포함하며, 부서가 없는 사원은 departmentName이 null입니다. "
                    + "size는 기본 20, 최대 100이고 sort는 무시합니다. "
                    + "기업관리자만 호출할 수 있습니다."
    )
    @GetMapping("/requests")
    public ResponseEntity<Page<LeaveManageResponse>> listRequests(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestParam int year,
            @RequestParam(required = false) LeaveRequestStatus status,
            @RequestParam(required = false) Long memberId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        Long companyId = loginMember.getCompanyId();

        return ResponseEntity.ok(
                leaveRequestService.getManageRequests(companyId, year, status, memberId, pageable)
        );
    }

}
