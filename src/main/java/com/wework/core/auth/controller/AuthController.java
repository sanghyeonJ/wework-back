package com.wework.core.auth.controller;

import com.wework.core.auth.service.AuthService;
import com.wework.core.auth.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "로그인", description = "ACTIVE 회원만 가능, 실패는 모두 INVALID_CREDENTIALS")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "토큰 재발급", description = "리프레시 토큰 회전 (이전 리프레시 토큰은 삭제)")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @Operation(summary = "로그아웃", description = "로그인 필요, 이 기기의 리프레시 토큰만 삭제")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
    }

    @Operation(summary = "초대 링크 확인", description = "비밀번호 설정 화면 진입 시 호출, 이메일·이름·회사명 반환")
    @GetMapping("/invitations/{token}")
    public InvitationResponse getInvitation(@PathVariable String token) {
        return authService.getInvitation(token);
    }

    @Operation(summary = "초대 수락", description = "비밀번호 설정 → ACTIVE, 이후 로그인 화면으로 이동 (8~64자, 영문·숫자·특수문자 포함)")
    @PostMapping("/invitations/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptInvitation(@Valid @RequestBody InvitationAcceptRequest request) {
        authService.acceptInvitation(request);
    }

}
