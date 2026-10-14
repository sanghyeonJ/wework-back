package com.wework.core.member;

import com.wework.core.member.dto.MeResponse;
import com.wework.global.error.BusinessException;
import com.wework.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MeService {

    private final MemberRepository memberRepository;

    // 내 정보
    public MeResponse getMe(Long memberId) {
        return MeResponse.of(findActiveMember(memberId));
    }

    // 토큰의 회원 id로 본인 조회 (재직 중이 아니면 401)
    private Member findActiveMember(Long memberId) {
        // 예외적으로 findById 사용: id가 토큰에서 온 "본인" 값이고, 최고관리자는 회사가 없어서 companyId 조건을 쓸 수 없음
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return member;
    }

}
