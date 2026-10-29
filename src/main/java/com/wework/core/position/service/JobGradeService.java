package com.wework.core.position.service;

import com.wework.core.company.entity.Company;
import com.wework.core.company.repository.CompanyRepository;
import com.wework.core.member.repository.MemberRepository;
import com.wework.core.position.entity.JobGrade;
import com.wework.core.position.repository.JobGradeRepository;
import com.wework.core.position.dto.PositionRequest;
import com.wework.core.position.dto.PositionResponse;
import com.wework.core.position.error.PositionErrorCode;
import com.wework.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobGradeService {

    private final JobGradeRepository jobGradeRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;

    // 목록 (level 작은 순 = 높은 직급부터)
    public List<PositionResponse> getJobGrades(Long companyId) {
        return jobGradeRepository.findAllByCompanyIdOrderByLevelAscIdAsc(companyId)
                .stream()
                .map(PositionResponse::of)
                .toList();
    }

    // 생성
    @Transactional
    public PositionResponse createJobGrade(Long companyId, PositionRequest request) {
        String name = request.getName().trim();

        if (jobGradeRepository.existsByCompanyIdAndName(companyId, name)) {
            throw new BusinessException(PositionErrorCode.JOB_GRADE_NAME_DUPLICATED);
        }

        Company company = companyRepository.getReferenceById(companyId);
        JobGrade jobGrade = JobGrade.create(company, name, request.getLevel());
        jobGradeRepository.save(jobGrade);

        return PositionResponse.of(jobGrade);
    }

    // 수정 (이름 + 순서)
    @Transactional
    public PositionResponse updateJobGrade(Long companyId, Long jobGradeId, PositionRequest request) {
        JobGrade jobGrade = getJobGrade(companyId, jobGradeId);
        String name = request.getName().trim();

        // 이름 중복 (나 자신 제외)
        if (jobGradeRepository.existsByCompanyIdAndNameAndIdNot(companyId, name, jobGradeId)) {
            throw new BusinessException(PositionErrorCode.JOB_GRADE_NAME_DUPLICATED);
        }

        jobGrade.update(name, request.getLevel());
        return PositionResponse.of(jobGrade);
    }

    // 삭제 (실제 삭제, 사용 중이면 거절)
    @Transactional
    public void deleteJobGrade(Long companyId, Long jobGradeId) {
        JobGrade jobGrade = getJobGrade(companyId, jobGradeId);

        // 이 직급을 가진 회원이 있으면 거절 (퇴사자 포함)
        if (memberRepository.existsByJobGradeId(jobGradeId)) {
            throw new BusinessException(PositionErrorCode.JOB_GRADE_IN_USE);
        }

        jobGradeRepository.delete(jobGrade);
    }

    // ===== 내부 확인용 =====

    // 내 회사의 직급 조회 (없거나 다른 회사면 404)
    private JobGrade getJobGrade(Long companyId, Long jobGradeId) {
        return jobGradeRepository.findByIdAndCompanyId(jobGradeId, companyId)
                .orElseThrow(() -> new BusinessException(PositionErrorCode.JOB_GRADE_NOT_FOUND));
    }


}
