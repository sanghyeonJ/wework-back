package com.wework.core.position;

import com.wework.core.position.dto.PositionRequest;
import com.wework.core.position.dto.PositionResponse;
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

@Tag(name = "기업관리자 - 직급")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/company/job-grades")
public class JobGradeController {

    private final JobGradeService jobGradeService;

    @Operation(summary = "직급 목록", description = "level 작은 순 (높은 직급부터)")
    @GetMapping
    public ResponseEntity<List<PositionResponse>> getJobGrades(
            @AuthenticationPrincipal LoginMember loginMember
            ) {
        return ResponseEntity.ok(jobGradeService.getJobGrades(loginMember.getCompanyId()));
    }

    @Operation(summary = "직급 생성")
    @PostMapping
    public ResponseEntity<PositionResponse> createJobGrade(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody PositionRequest request
            ) {
        PositionResponse response = jobGradeService.createJobGrade(loginMember.getCompanyId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "직급 수정", description = "이름과 level을 함께 보냄")
    @PatchMapping("/{jobGradeId}")
    public ResponseEntity<PositionResponse> updateJogGrade(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long jobGradeId,
            @Valid @RequestBody PositionRequest request
    ) {
        return ResponseEntity.ok(
                jobGradeService.updateJobGrade(loginMember.getCompanyId(), jobGradeId, request)
        );
    }

    @Operation(summary = "직급 삭제", description = "사용 중(퇴사자 포함)이면 거절")
    @DeleteMapping("/{jobGradeId}")
    public ResponseEntity<Void> deleteJobGrade(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long jobGradeId
    ) {
        jobGradeService.deleteJobGrade(loginMember.getCompanyId(), jobGradeId);
        return ResponseEntity.noContent().build();
    }

}
