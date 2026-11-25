package com.wework.core.member.controller;

import com.wework.core.member.dto.ApproverCandidateResponse;
import com.wework.core.member.service.MemberService;
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

import java.util.List;

@Tag(name = "사원 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberLookupController {

    private final MemberService memberService;

    @Operation(
            summary = "결재자 후보 조회"
    )
    @GetMapping("/approver-candidates")
    public ResponseEntity<List<ApproverCandidateResponse>> getApproverCandidates(
            @AuthenticationPrincipal LoginMember loginMember,
            @RequestParam(required = false) String keyword
            ) {
        return ResponseEntity.ok(memberService.getApproverCandidates(loginMember.getCompanyId(), loginMember.getMemberId(), keyword));
    }

}
