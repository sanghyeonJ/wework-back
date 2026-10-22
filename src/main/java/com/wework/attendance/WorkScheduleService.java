package com.wework.attendance;

import com.wework.attendance.dto.WorkScheduleRequest;
import com.wework.attendance.dto.WorkScheduleResponse;
import com.wework.core.company.CompanyRepository;
import com.wework.core.member.Member;
import com.wework.core.member.MemberErrorCode;
import com.wework.core.member.MemberRepository;
import com.wework.core.member.MemberStatus;
import com.wework.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WorkScheduleService {

    // 설정이 하나도 없을 때 쓰는 기본값
    private static final LocalTime DEFAULT_START_TIME = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_END_TIME = LocalTime.of(18, 0);
    private static final int DEFAULT_GRACE_MINUTES = 0;

    private final WorkScheduleRepository workScheduleRepository;
    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;

    // ===== 조회 =====

    // 회사 + 개인 설정 전체 목록
    public List<WorkScheduleResponse> getSchedules(Long companyId) {
        return workScheduleRepository.findAllWithMemberByCompanyId(companyId).stream()
                .map(WorkScheduleResponse::of)
                .toList();
    }

    // 관리자 화면용: 이 사원에게 실제 적용되는 근무시간
    public EffectiveWorkSchedule getEffective(Long companyId, Long memberId) {
        findMember(memberId, companyId);
        return findEffective(companyId, memberId);
    }

    // 출근 판단용 (AttendanceService가 호출): 개인 > 회사 > 기본값
    // memberId가 토큰에서 온 값이라 사원 확인은 생략
    public EffectiveWorkSchedule findEffective(Long companyId, Long memberId) {
        WorkSchedule memberSchedule = workScheduleRepository
                .findByCompanyIdAndScopeTypeAndMemberId(companyId, WorkScheduleScope.MEMBER, memberId)
                .orElse(null);
        if (memberSchedule != null) {
            return toEffective(WorkScheduleSource.MEMBER, memberSchedule);
        }

        WorkSchedule companySchedule = workScheduleRepository
                .findByCompanyIdAndScopeType(companyId, WorkScheduleScope.COMPANY)
                .orElse(null);
        if (companySchedule != null) {
            return toEffective(WorkScheduleSource.COMPANY, companySchedule);
        }

        return EffectiveWorkSchedule.of(WorkScheduleSource.DEFAULT, DEFAULT_START_TIME, DEFAULT_END_TIME, DEFAULT_GRACE_MINUTES);
    }

    // ===== 저장 (없으면 생성, 있으면 수정) =====
    @Transactional
    public WorkScheduleResponse saveCompanySchedule(Long companyId, WorkScheduleRequest request) {
        checkWorkTime(request);  // [서비스 판단] 입력값 검사

        WorkSchedule schedule = workScheduleRepository
                .findByCompanyIdAndScopeType(companyId, WorkScheduleScope.COMPANY)
                .orElse(null);

        if (schedule == null) {
            schedule = workScheduleRepository.save(WorkSchedule.createForCompany(
                    companyRepository.getReferenceById(companyId),
                    request.getStartTime(), request.getEndTime(), request.getGraceMinutes()
            ));
        } else {
            schedule.update(request.getStartTime(), request.getEndTime(), request.getGraceMinutes());
        }
        return WorkScheduleResponse.of(schedule);
    }

    @Transactional
    public WorkScheduleResponse saveMemberSchedule(Long companyId, Long memberId, WorkScheduleRequest request) {
        Member member = findMember(memberId, companyId);
        if (member.getStatus() == MemberStatus.RESIGNED) {
            throw new BusinessException(MemberErrorCode.MEMBER_RESIGNED);
        }
        checkWorkTime(request);

        WorkSchedule schedule = workScheduleRepository
                .findByCompanyIdAndScopeTypeAndMemberId(companyId, WorkScheduleScope.MEMBER, memberId)
                .orElse(null);

        if (schedule == null) {
            schedule = workScheduleRepository.save(WorkSchedule.createForMember(
                    companyRepository.getReferenceById(companyId), member,
                    request.getStartTime(), request.getEndTime(), request.getGraceMinutes()
            ));
        } else {
            schedule.update(request.getStartTime(), request.getEndTime(), request.getGraceMinutes());
        }
        return WorkScheduleResponse.of(schedule);
    }

    // ===== 삭제 (개인 설정만, 삭제하면 회사 설정으로 돌아감) =====
    @Transactional
    public void deleteMemberSchedule(Long companyId, Long memberId) {
        findMember(memberId, companyId);

        WorkSchedule schedule = workScheduleRepository
                .findByCompanyIdAndScopeTypeAndMemberId(companyId, WorkScheduleScope.MEMBER, memberId)
                .orElseThrow(() -> new BusinessException(AttendanceErrorCode.WORK_SCHEDULE_NOT_FOUND));

        workScheduleRepository.delete(schedule);
    }


    // ===== 내부 도우미 =====
    private Member findMember(Long memberId, Long companyId) {
        return memberRepository.findByIdAndCompanyId(memberId, companyId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    // 퇴근 기준 시각이 출근 기준 시각보다 늦어야 함
    private void checkWorkTime(WorkScheduleRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException(AttendanceErrorCode.INVALID_WORK_TIME);
        }
    }

    private EffectiveWorkSchedule toEffective(WorkScheduleSource source, WorkSchedule schedule) {
        return EffectiveWorkSchedule.of(source, schedule.getStartTime(), schedule.getEndTime(), schedule.getGraceMinutes());
    }

}
