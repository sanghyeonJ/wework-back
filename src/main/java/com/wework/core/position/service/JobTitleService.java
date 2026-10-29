package com.wework.core.position.service;

import com.wework.core.company.entity.Company;
import com.wework.core.company.repository.CompanyRepository;
import com.wework.core.member.repository.MemberRepository;
import com.wework.core.position.entity.JobTitle;
import com.wework.core.position.repository.JobTitleRepository;
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
public class JobTitleService {

    private final JobTitleRepository jobTitleRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;

    // 목록 (level 작은 순 = 높은 직책부터)
    public List<PositionResponse> getJobTitles(Long companyId) {
        return jobTitleRepository.findAllByCompanyIdOrderByLevelAscIdAsc(companyId)
                .stream()
                .map(PositionResponse::of)
                .toList();
    }

    // 생성
    @Transactional
    public PositionResponse createJobTitle(Long companyId, PositionRequest request) {
        String name = request.getName().trim();

        if (jobTitleRepository.existsByCompanyIdAndName(companyId, name)) {
            throw new BusinessException(PositionErrorCode.JOB_TITLE_NAME_DUPLICATED);
        }

        Company company = companyRepository.getReferenceById(companyId);
        JobTitle jobTitle = JobTitle.create(company, name, request.getLevel());
        jobTitleRepository.save(jobTitle);

        return PositionResponse.of(jobTitle);
    }

    // 수정 (이름 + 순서)
    @Transactional
    public PositionResponse updateJobTitle(Long companyId, Long jobTitleId, PositionRequest request) {
        JobTitle jobTitle = getJobTitle(companyId, jobTitleId);
        String name = request.getName().trim();

        // 이름 중복 (나 자신 제외)
        if (jobTitleRepository.existsByCompanyIdAndNameAndIdNot(companyId, name, jobTitleId)) {
            throw new BusinessException(PositionErrorCode.JOB_TITLE_NAME_DUPLICATED);
        }

        jobTitle.update(name, request.getLevel());
        return PositionResponse.of(jobTitle);
    }

    // 삭제 (실제 삭제, 사용 중이면 거절)
    @Transactional
    public void deleteJobTitle(Long companyId, Long jobTitleId) {
        JobTitle jobTitle = getJobTitle(companyId, jobTitleId);

        // 이 직책을 가진 회원이 있으면 거절 (퇴사자 포함)
        if (memberRepository.existsByJobTitleId(jobTitleId)) {
            throw new BusinessException(PositionErrorCode.JOB_TITLE_IN_USE);
        }

        jobTitleRepository.delete(jobTitle);
    }

    // ===== 내부 확인용 =====

    // 내 회사의 직책 조회 (없거나 다른 회사면 404)
    private JobTitle getJobTitle(Long companyId, Long jobTitleId) {
        return jobTitleRepository.findByIdAndCompanyId(jobTitleId, companyId)
                .orElseThrow(() -> new BusinessException(PositionErrorCode.JOB_TITLE_NOT_FOUND));
    }

}
