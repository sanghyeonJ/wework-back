package com.wework.core.member.dto;

import lombok.Getter;

import java.time.Instant;

@Getter
public class MemberInviteResponse {

    private final MemberResponse member;
    private final String inviteUrl;     // M6 전까지 테스트용 (메일 대신 응답으로 확인)
    private final Instant expiresAt;

    private MemberInviteResponse(MemberResponse member, String inviteUrl, Instant expiresAt) {
        this.member = member;
        this.inviteUrl = inviteUrl;
        this.expiresAt = expiresAt;
    }

    public static MemberInviteResponse of(MemberResponse member, String inviteUrl, Instant expiresAt) {
        return new MemberInviteResponse(member, inviteUrl, expiresAt);
    }

}
