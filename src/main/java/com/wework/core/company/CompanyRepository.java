package com.wework.core.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    // 회사 이름으로 1개 찾기
    Optional<Company> findByName(String name);

}
