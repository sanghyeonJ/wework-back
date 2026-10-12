package com.wework.global.security;

import com.wework.core.member.MemberRole;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class LoginMember {

    private final Long memberId;
    private final Long companyId;
    private final MemberRole role;

}
