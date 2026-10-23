package com.wework.attendance;

import com.wework.attendance.dto.WorkScheduleRequest;
import com.wework.attendance.dto.WorkScheduleResponse;
import com.wework.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "근태 관리 (기업관리자)")
@RestController
@RequestMapping("/api/attendance/manage")
@RequiredArgsConstructor
public class AttendanceManageController {

    private final WorkScheduleService workScheduleService;

    @Operation(summary = "근무시간 설정 목록", description = "회사 일괄 설정과 개인 설정을 모두 돌려줍니다. 회사 설정이 맨 위에 옵니다.")
    @GetMapping("/schedules")
    public ResponseEntity<List<WorkScheduleResponse>> getSchedules(@AuthenticationPrincipal LoginMember loginMember) {
        return ResponseEntity.ok(workScheduleService.getSchedules(loginMember.getCompanyId()));
    }

    @Operation(summary = "회사 일괄 근무시간 저장", description = "없으면 생성하고, 있으면 수정합니다. 개인 설정이 없는 사원에게 적용됩니다.")
    @PutMapping("/schedules/company")
    public ResponseEntity<WorkScheduleResponse> saveCompanySchedule(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody WorkScheduleRequest request
            ) {
        return ResponseEntity.ok(workScheduleService.saveCompanySchedule(loginMember.getCompanyId(), request));
    }

    @Operation(summary = "개인 근무시간 저장", description = "없으면 생성하고, 있으면 수정합니다. 회사 설정보다 우선 적용됩니다. 퇴사자는 설정할 수 없습니다.")
    @PutMapping("/schedules/members/{memberId}")
    public ResponseEntity<WorkScheduleResponse> saveMemberSchedule(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long memberId,
            @Valid @RequestBody WorkScheduleRequest request
    ) {
        return ResponseEntity.ok(workScheduleService.saveMemberSchedule(loginMember.getCompanyId(), memberId, request));
    }

    @Operation(summary = "개인 근무시간 삭제", description = "개인 설정을 지우면 회사 설정(없으면 기본값)으로 돌아갑니다.")
    @DeleteMapping("/schedules/members/{memberId}")
    public ResponseEntity<Void> deleteMemberSchedule(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long memberId
    ) {
        workScheduleService.deleteMemberSchedule(loginMember.getCompanyId(), memberId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "사원에게 적용되는 근무시간", description = "개인 > 회사 > 기본값 순으로 실제 적용되는 근무시간과 출처(MEMBER/COMPANY/DEFAULT)를 돌려줍니다.")
    @GetMapping("/schedules/effective")
    public ResponseEntity<EffectiveWorkSchedule> getEffective(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestParam Long memberId
    ) {
        return ResponseEntity.ok(workScheduleService.getEffective(loginMember.getCompanyId(), memberId));
    }

}
