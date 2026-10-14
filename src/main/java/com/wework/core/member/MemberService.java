package com.wework.core.member;

import com.wework.core.auth.MemberToken;
import com.wework.core.auth.MemberTokenRepository;
import com.wework.core.auth.MemberTokenType;
import com.wework.core.auth.RefreshTokenRepository;
import com.wework.core.company.CompanyRepository;
import com.wework.core.department.Department;
import com.wework.core.department.DepartmentErrorCode;
import com.wework.core.department.DepartmentRepository;
import com.wework.core.department.DepartmentStatus;
import com.wework.core.member.dto.*;
import com.wework.core.position.*;
import com.wework.global.error.BusinessException;
import com.wework.global.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberTokenRepository memberTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final JobGradeRepository jobGradeRepository;
    private final JobTitleRepository jobTitleRepository;

    @Value("${app.invite.url}")
    private String inviteUrl;

    @Value("${app.invite.expire-hours}")
    private long inviteExpireHours;

    // ===== 조회 =====

    // 목록 (검색 + 페이징)
    public Page<MemberResponse> getMembers(Long companyId, MemberStatus status, Long departmentId, String keyword, Pageable pageable) {
        String pattern = (keyword == null || keyword.isBlank())
                ? null
                : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";

        return memberRepository.search(companyId, status, departmentId, pattern, pageable)
                .map(MemberResponse::of);
    }

    // 상세
    public MemberResponse getMember(Long companyId, Long memberId) {
        return MemberResponse.of(findMember(memberId, companyId));
    }

    // ===== 계정 발급 =====
    @Transactional
    public MemberInviteResponse createMember(Long companyId, MemberCreateRequest request) {
        String email = Member.normalizeEmail(request.getEmail());

        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(MemberErrorCode.EMAIL_DUPLICATED);
        }

        Member member = Member.builder()
                .company(companyRepository.getReferenceById(companyId))
                .department(findDepartment(request.getDepartmentId(), companyId))
                .jobGrade(findJobGrade(request.getJobGradeId(), companyId))
                .jobTitle(findJobTitle(request.getJobTitleId(), companyId))
                .email(email)
                .name(request.getName().trim())
                .hireDate(request.getHireDate())
                .build();

        memberRepository.save(member);
        return issueInvitation(member);
    }

    // ===== 수정 =====
    @Transactional
    public MemberResponse updateMember(Long companyId, Long memberId, MemberUpdateRequest request) {
        Member member = findMember(memberId, companyId);
        checkNotResigned(member);

        Department newDepartment = findDepartment(request.getDepartmentId(), companyId);

        // 부서가 바뀌는데 부서장이면 거절
        Long currentDepartmentId = (member.getDepartment() != null) ? member.getDepartment().getId() : null;
        boolean departmentChanged = Objects.equals(currentDepartmentId, request.getDepartmentId());
        if (departmentChanged && isLeader(member)) {
            throw new BusinessException(MemberErrorCode.LEADER_CANNOT_CHANGE_DEPARTMENT);
        }

        member.update(
                request.getName().trim(),
                newDepartment,
                findJobGrade(request.getJobGradeId(), companyId),
                findJobTitle(request.getJobTitleId(), companyId),
                request.getHireDate()
        );
        return MemberResponse.of(member);
    }

    // ===== 권한 변경 =====
    @Transactional
    public MemberResponse changeRole(Long companyId, Long memberId, MemberRoleRequest request) {
        MemberRole newRole = request.getRole();
        if (newRole == MemberRole.SUPER_ADMIN) {
            throw new BusinessException(MemberErrorCode.INVALID_MEMBER_ROLE);
        }

        Member member = findMember(memberId, companyId);
        checkNotResigned(member);

        if (newRole == MemberRole.EMPLOYEE) {
            checkNotLastAdmin(member, companyId);
        }

        member.changeRole(newRole);
        return MemberResponse.of(member);
    }

    // ===== 퇴사 =====
    @Transactional
    public MemberResponse resignMember(Long companyId, Long memberId) {
        Member member = findMember(memberId, companyId);
        checkNotResigned(member);

        if (isLeader(member)) {
            throw new BusinessException(MemberErrorCode.LEADER_CANNOT_RESIGN);
        }
        checkNotLastAdmin(member, companyId);

        member.resign();
        refreshTokenRepository.deleteAllByMemberId(member.getId());
        invalidateInvitations(member, Instant.now());
        return MemberResponse.of(member);
    }

    // ===== 초대 재발급 =====
    @Transactional
    public MemberInviteResponse reissueInvitation(Long companyId, Long memberId) {
        Member member = findMember(memberId, companyId);

        if (member.getStatus() != MemberStatus.INVITED) {
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_INVITED);
        }
        return issueInvitation(member);
    }


    // ===== 내부 도우미 =====

    // 초대 토큰 발급 (이전 초대 무효화 → 새 토큰 저장 → 링크 반환)
    private MemberInviteResponse issueInvitation(Member member) {
        Instant now = Instant.now();
        invalidateInvitations(member, now);

        String rawToken = TokenHasher.generateToken();
        Instant expiresAt = now.plus(Duration.ofHours(inviteExpireHours));
        memberTokenRepository.save(
                MemberToken.create(member, MemberTokenType.INVITE, TokenHasher.hash(rawToken), expiresAt)
        );

        String link = inviteUrl + "?token=" + rawToken;
        return MemberInviteResponse.of(MemberResponse.of(member), link, expiresAt);
    }

    // 아직 안 쓴 초대 토큰을 사용 처리 (dirty checking)
    private void invalidateInvitations(Member member, Instant now) {
        memberTokenRepository
                .findAllByMemberIdAndTypeAndUsedAtIsNull(member.getId(), MemberTokenType.INVITE)
                .forEach(token -> token.markUsed(now));
    }

    private Member findMember(Long memberId, Long companyId) {
        return memberRepository.findByIdAndCompanyId(memberId, companyId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    // null이면 미지정, 있으면 내 회사 + ACTIVE 부서만
    private Department findDepartment(Long departmentId, Long companyId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findByIdAndCompanyIdAndStatus(departmentId, companyId, DepartmentStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(DepartmentErrorCode.DEPARTMENT_NOT_FOUND));
    }

    private JobGrade findJobGrade(Long jobGradeId, Long companyId) {
        if (jobGradeId == null) {
            return null;
        }
        return jobGradeRepository.findByIdAndCompanyId(jobGradeId, companyId)
                .orElseThrow(() -> new BusinessException(PositionErrorCode.JOB_GRADE_NOT_FOUND));
    }

    private JobTitle findJobTitle(Long jobTitleId, Long companyId) {
        if (jobTitleId == null) {
            return null;
        }
        return jobTitleRepository.findByIdAndCompanyId(jobTitleId, companyId)
                .orElseThrow(() -> new BusinessException(PositionErrorCode.JOB_TITLE_NOT_FOUND));
    }

    private void checkNotResigned(Member member) {
        if (member.getStatus() == MemberStatus.RESIGNED) {
            throw new BusinessException(MemberErrorCode.MEMBER_RESIGNED);
        }
    }

    private boolean isLeader(Member member) {
        return departmentRepository.existsByLeaderIdAndStatus(member.getId(), DepartmentStatus.ACTIVE);
    }

    // 재직 중인 기업관리자가 이 사람 1명뿐이면 거절
    private void checkNotLastAdmin(Member member, Long companyId) {
        if (member.getRole() != MemberRole.COMPANY_ADMIN || member.getStatus() != MemberStatus.ACTIVE) {
            return;
        }
        long adminCount = memberRepository.countByCompanyIdAndRoleAndStatus(
                companyId, MemberRole.COMPANY_ADMIN, MemberStatus.ACTIVE);
        if (adminCount <= 1) {
            throw new BusinessException(MemberErrorCode.LAST_COMPANY_ADMIN);
        }
    }
}
