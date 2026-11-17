package com.wework.core.leave.repository;

import com.wework.core.company.entity.Company;
import com.wework.core.leave.entity.LeaveGrantRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeaveGrantRuleRepository extends JpaRepository<LeaveGrantRule, Long> {

    List<LeaveGrantRule> findAllByCompanyIdOrderByServiceYearAsc(Long companyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LeaveGrantRule r where r.company.id = :companyId")
    void deleteAllByCompanyId(@Param("companyId") Long companyId);

}
