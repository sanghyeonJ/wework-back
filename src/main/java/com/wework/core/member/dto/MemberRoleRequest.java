package com.wework.core.member.dto;

import com.wework.core.member.MemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberRoleRequest {

    @NotNull
    private MemberRole role;

}
