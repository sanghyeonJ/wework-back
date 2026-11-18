package com.wework.core.company.repository;

import com.wework.core.company.entity.Company;
import com.wework.core.company.entity.CompanyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    // 사업자번호로 1개 찾기
    Optional<Company> findByBusinessNumber(String businessNumber);

    // 사업자번호 중복확인
    boolean existsByBusinessNumber(String businessNumber);

    // 국가 코드와 상태로 회사 목록 조회
    List<Company> findAllByCountryCodeAndStatus(String countryCode, CompanyStatus status);

}
