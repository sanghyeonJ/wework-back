package com.wework.core.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InvitationAcceptRequest {

    @NotBlank
    private String token;

    // 8~64자, 영문·숫자·특수문자 각각 1개 이상, 공백 불가
    @NotBlank
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s])\\S{8,64}$")
    private String password;

}
