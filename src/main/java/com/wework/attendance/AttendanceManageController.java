package com.wework.attendance;

import com.wework.attendance.dto.AttendanceRecordResponse;
import com.wework.attendance.dto.WorkScheduleBulkRequest;
import com.wework.attendance.dto.WorkScheduleRequest;
import com.wework.attendance.dto.WorkScheduleResponse;
import com.wework.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "근태 관리 (기업관리자)")
@RestController
@RequestMapping("/api/attendance/manage")
@RequiredArgsConstructor
public class AttendanceManageController {

    private final WorkScheduleService workScheduleService;
    private final AttendanceService attendanceService;

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

    @Operation(summary = "여러 사원 근무시간 일괄 저장", description = "선택한 사원들에게 같은 개인 근무시간을 한 번에 저장합니다. 이미 개인 설정이 있는 사원은 값이 바뀌고, 없으면 새로 생깁니다. 한 명이라도 내 회사 사원이 아니거나(404) 퇴사자면(409) 전체가 취소됩니다. 한 번에 최대 100명입니다.")
    @PutMapping("/schedules/members")
    public ResponseEntity<List<WorkScheduleResponse>> saveMemberSchedules(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody WorkScheduleBulkRequest request
            ) {
        return ResponseEntity.ok(workScheduleService.saveMemberSchedules(loginMember.getCompanyId(), request));
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

    @Operation(summary = "근태 조회 (날짜별)", description = "그날 재직 중이던 사원과 출퇴근 기록을 페이지 단위로 돌려줍니다. 기록이 없는 사원은 attendance가 null 입니다. 부서를 고르면 그 부서(includeSubDepartments=true면 하위 부서 포함) 사원만 나옵니다. 정렬은 이름순으로 고정이며 sort 값은 무시됩니다. size는 최대 100입니다.")
    @GetMapping("/records")
    public ResponseEntity<Page<AttendanceRecordResponse>> getRecords(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "false") boolean includeSubDepartments,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
            ) {
        return ResponseEntity.ok(
                attendanceService.getRecords(loginMember.getCompanyId(), date, departmentId, includeSubDepartments, pageable)
        );
    }

}
