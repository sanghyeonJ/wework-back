package com.wework.core.department.controller;

import com.wework.core.department.dto.DepartmentCreateRequest;
import com.wework.core.department.dto.DepartmentLeaderRequest;
import com.wework.core.department.dto.DepartmentResponse;
import com.wework.core.department.dto.DepartmentUpdateRequest;
import com.wework.core.department.service.DepartmentService;
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

@Tag(name = "기업관리자 - 부서")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/company/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "부서 목록")
    @GetMapping
    public ResponseEntity<List<DepartmentResponse>> getDepartments(
            @AuthenticationPrincipal LoginMember loginMember
            ) {
        return ResponseEntity.ok(departmentService.getDepartments(loginMember.getCompanyId()));
    }

    @Operation(summary = "부서 생성", description = "parentId가 null이면 최상위 부서")
    @PostMapping
    public ResponseEntity<DepartmentResponse> createDepartment(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody DepartmentCreateRequest request
            ) {
        DepartmentResponse response = departmentService.createDepartment(loginMember.getCompanyId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "부서 수정", description = "이름과 상위 부서를 함께 보냄 (최상위 부서는 parentId null)")
    @PatchMapping("/{departmentId}")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long departmentId,
            @Valid @RequestBody DepartmentUpdateRequest request
            ) {
        return ResponseEntity.ok(
                departmentService.updateDepartment(loginMember.getCompanyId(), departmentId, request)
        );
    }

    @Operation(summary = "부서장 지정", description = "leaderId가 null이면 해제")
    @PatchMapping("/{departmentId}/leader")
    public ResponseEntity<DepartmentResponse> changeLeader(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long departmentId,
            @RequestBody DepartmentLeaderRequest request
            ) {
        return ResponseEntity.ok(
                departmentService.changeLeader(loginMember.getCompanyId(), departmentId, request)
        );
    }

    @Operation(summary = "부서 삭제", description = "INACTIVE 처리, 하위 부서나 재직자가 있으면 거절")
    @DeleteMapping("/{departmentId}")
    public ResponseEntity<Void> deleteDepartment(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long departmentId
    ) {
        departmentService.deleteDepartment(loginMember.getCompanyId(), departmentId);
        return ResponseEntity.noContent().build();
    }

}
