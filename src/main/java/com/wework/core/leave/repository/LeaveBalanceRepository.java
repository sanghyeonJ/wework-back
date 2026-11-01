package com.wework.core.leave.repository;

import com.wework.core.leave.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByCompanyIdAndMemberIdAndYear(Long companyId, Long memberId, int year);

}
