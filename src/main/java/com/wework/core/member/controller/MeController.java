package com.wework.core.member.controller;

import com.wework.core.member.service.MeService;
import com.wework.core.member.dto.MeResponse;
import com.wework.global.security.LoginMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내 정보")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
public class MeController {

    private final MeService meService;

    @Operation(summary = "내 정보", description = "로그인한 회원 정보 (켜진 모듈 목록은 M4에서 추가)")
    @GetMapping
    public ResponseEntity<MeResponse> getMe(@AuthenticationPrincipal LoginMember loginMember) {
        return ResponseEntity.ok(meService.getMe(loginMember.getMemberId()));
    }

}
