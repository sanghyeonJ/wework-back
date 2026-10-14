package com.wework.core.auth.dto;

import com.wework.core.member.Member;
import lombok.Getter;

import java.time.Instant;

@Getter
public class InvitationResponse {

    private final String email;
    private final String name;
    private final String companyName;
    private final Instant expiresAt;

    private InvitationResponse(Member member, Instant expiresAt) {
        this.email = member.getEmail();
        this.name = member.getName();
        this.companyName = member.getCompany().getName();
        this.expiresAt = expiresAt;
    }

    public static InvitationResponse of(Member member, Instant expiresAt) {
        return new InvitationResponse(member, expiresAt);
    }

}
