package com.wework.core.department;

import com.wework.core.company.Company;
import com.wework.core.company.CompanyRepository;
import com.wework.core.department.dto.DepartmentCreateRequest;
import com.wework.core.department.dto.DepartmentLeaderRequest;
import com.wework.core.department.dto.DepartmentResponse;
import com.wework.core.department.dto.DepartmentUpdateRequest;
import com.wework.core.member.Member;
import com.wework.core.member.MemberRepository;
import com.wework.core.member.MemberStatus;
import com.wework.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;

    // 목록 (내 회사의 ACTIVE 부서, id 순)
    public List<DepartmentResponse> getDepartments(Long companyId) {
        return departmentRepository.findAllByCompanyIdAndStatus(companyId, DepartmentStatus.ACTIVE)
                .stream()
                .sorted(Comparator.comparing(Department::getId))
                .map(DepartmentResponse::of)
                .toList();
    }

    // 생성
    @Transactional
    public DepartmentResponse createDepartment(Long companyId, DepartmentCreateRequest request) {
        String name = request.getName().trim();

        // 이름 중복 (ACTIVE끼리만)
        if(departmentRepository.existsByCompanyIdAndNameAndStatus(companyId, name, DepartmentStatus.ACTIVE)) {
            throw new BusinessException(DepartmentErrorCode.DEPARTMENT_NAME_DUPLICATED);
        }

        Department parent = null;
        if(request.getParentId() == null) {
            // 최상위 부서로 생성 → 이미 있으면 거절
            validateNoRoot(companyId);
        } else {
            // 상위 부서는 내 회사의 ACTIVE 부서만
            parent = getActiveDepartment(companyId, request.getParentId());
        }

        Company company = companyRepository.getReferenceById(companyId);
        Department department = Department.create(company, parent, name);
        departmentRepository.save(department);

        return DepartmentResponse.of(department);
    }

    // 수정 (이름 + 상위 부서)
    @Transactional
    public DepartmentResponse updateDepartment(Long companyId, Long departmentId, DepartmentUpdateRequest request) {
        Department department = getActiveDepartment(companyId, departmentId);
        String name = request.getName().trim();

        // 이름 중복 (나 자신 제외)
        if (departmentRepository.existsByCompanyIdAndNameAndStatusAndIdNot(
                companyId, name, DepartmentStatus.ACTIVE, departmentId
        )) {
            throw new BusinessException(DepartmentErrorCode.DEPARTMENT_NAME_DUPLICATED);
        }

        Long parentId = request.getParentId();

        if (department.isRoot()) {
            // 최상위 부서는 이동 불가
            if (parentId != null) {
                throw new BusinessException(DepartmentErrorCode.ROOT_DEPARTMENT_CANNOT_MOVE);
            }
        } else if (parentId == null) {
            // 일반 부서를 최상위로 올리려는 경우 → 최상위는 회사당 1개
            validateNoRoot(companyId);
        } else {
            Department newParent = getActiveDepartment(companyId, parentId);
            validateNotCircular(department, newParent);
            department.changeParent(newParent);
        }

        department.changeName(name);
        return DepartmentResponse.of(department);
    }

    // 부서장 지정 / 해제
    @Transactional
    public DepartmentResponse changeLeader(Long companyId, Long departmentId, DepartmentLeaderRequest request) {
        Department department = getActiveDepartment(companyId, departmentId);

        // null이면 해제
        if (request.getLeaderId() == null) {
            department.changeLeader(null);
            return DepartmentResponse.of(department);
        }

        // 같은 회사 회원인지 (다른 회사 id면 못 찾음)
        Member leader = memberRepository.findByIdAndCompanyId(request.getLeaderId(), companyId)
                .orElseThrow(() -> new BusinessException(DepartmentErrorCode.INVALID_DEPARTMENT_LEADER));

        // 그 부서 소속인지 + ACTIVE인지
        boolean inDepartment = leader.getDepartment() != null &&
                leader.getDepartment().getId().equals(departmentId);
        boolean active = leader.getStatus() == MemberStatus.ACTIVE;

        if (!inDepartment || !active) {
            throw new BusinessException(DepartmentErrorCode.INVALID_DEPARTMENT_LEADER);
        }

        department.changeLeader(leader);
        return DepartmentResponse.of(department);
    }

    // 삭제 (INACTIVE 처리)
    @Transactional
    public void deleteDepartment(Long companyId, Long departmentId) {
        Department department = getActiveDepartment(companyId, departmentId);

        // ACTIVE 하위 부서가 있으면 거절
        if (departmentRepository.existsByParentIdAndStatus(departmentId, DepartmentStatus.ACTIVE)) {
            throw new BusinessException(DepartmentErrorCode.DEPARTMENT_HAS_CHILDREN);
        }

        // 재직자(INVITED·ACTIVE)가 있으면 거절, 퇴사자는 무관
        if (memberRepository.existsByDepartmentIdAndStatusNot(departmentId, MemberStatus.RESIGNED)) {
            throw new BusinessException(DepartmentErrorCode.DEPARTMENT_HAS_MEMBERS);
        }

        department.changeLeader(null);
        department.deactivate();
    }

    // ===== 내부 확인용 =====

    // 내 회사의 ACTIVE 부서 조회 (없거나, 다른 회사거나, 삭제됐으면 404)
    private Department getActiveDepartment(Long companyId, Long departmentId) {
        return departmentRepository.findByIdAndCompanyIdAndStatus(departmentId, companyId, DepartmentStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(DepartmentErrorCode.DEPARTMENT_NOT_FOUND));
    }

    // 최상위 부서가 이미 있으면 거절
    private void validateNoRoot(Long companyId) {
        if (departmentRepository.existsByCompanyIdAndParentIsNullAndStatus(companyId, DepartmentStatus.ACTIVE)) {
            throw new BusinessException(DepartmentErrorCode.ROOT_DEPARTMENT_ALREADY_EXISTS);
        }
    }

    // 새 상위 부서에서 위로 올라가다가 나 자신이 나오면 순환
    private void validateNotCircular(Department department, Department newParent) {
        Department current = newParent;
        while (current != null) {
            if (current.getId().equals(department.getId())) {
                throw new BusinessException(DepartmentErrorCode.CIRCULAR_DEPARTMENT);
            }
            current = current.getParent();
        }
    }

}
