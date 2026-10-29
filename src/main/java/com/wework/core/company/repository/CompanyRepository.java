package com.wework.core.company.repository;

import com.wework.core.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    // 회사 이름으로 1개 찾기
    Optional<Company> findByBusinessNumber(String businessNumber);

    // 사업자번호 중복확인
    boolean existsByBusinessNumber(String businessNumber);

}
