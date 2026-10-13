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

@Tag(name = "기업관리자 - 직책")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/company/job-title")
public class JobTitleController {

    private final JobTitleService jobTitleService;

    @Operation(summary = "직책 목록", description = "level 작은 순 (높은 직책부터)")
    @GetMapping
    public ResponseEntity<List<PositionResponse>> getJobTitles(
            @AuthenticationPrincipal LoginMember loginMember
            ) {
        return ResponseEntity.ok(jobTitleService.getJobTitles(loginMember.getCompanyId()));
    }

    @Operation(summary = "직책 생성")
    @PostMapping
    public ResponseEntity<PositionResponse> createJobTitle(
            @AuthenticationPrincipal LoginMember loginMember,
            @Valid @RequestBody PositionRequest request
            ) {
        PositionResponse response = jobTitleService.createJobTitle(loginMember.getCompanyId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "직책 수정", description = "이름과 level을 함께 보냄")
    @PatchMapping("/{jobTitleId}")
    public ResponseEntity<PositionResponse> updateJobTitle(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long jobTitleId,
            @Valid @RequestBody PositionRequest request
    ) {
        return ResponseEntity.ok(
                jobTitleService.updateJobTitle(loginMember.getCompanyId(), jobTitleId, request)
        );
    }

    @Operation(summary = "직책 삭제", description = "사용 중(퇴사자 포함)이면 거절")
    @DeleteMapping("/{jobTitleId}")
    public ResponseEntity<Void> deleteJobTitle(
            @AuthenticationPrincipal LoginMember loginMember,
            @PathVariable Long jobTitleId
    ) {
        jobTitleService.deleteJobTitle(loginMember.getCompanyId(), jobTitleId);
        return ResponseEntity.noContent().build();
    }

}
