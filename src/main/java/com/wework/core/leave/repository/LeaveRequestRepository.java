package com.wework.core.leave.repository;

import com.wework.core.leave.entity.LeaveRequest;
import com.wework.core.leave.entity.LeaveRequestStatus;
import com.wework.core.leave.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @Query(
            value = """
                select r from LeaveRequest r
                where r.company.id = :companyId
                    and r.member.id = :memberId
                    and r.status in :statuses
                    and r.startDate <= :endDate
                    and r.endDate >= :startDate
            """
    )
    List<LeaveRequest> findOverlapping(@Param("companyId") Long companyId,
                                       @Param("memberId") Long memberId,
                                       @Param("statuses") List<LeaveRequestStatus> statuses,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    @Query(
            value = """
                select sum(r.days)
                from LeaveRequest r
                where r.company.id = :companyId
                    and r.member.id = :memberId
                    and r.status = :status
                    and r.leaveType in :types
                    and r.startDate >= :yearStart
                    and r.endDate <= :yearEnd
            """
    )
    BigDecimal sumDays(@Param("companyId") Long companyId,
                       @Param("memberId") Long memberId,
                       @Param("status") LeaveRequestStatus status,
                       @Param("types") List<LeaveType> types,
                       @Param("yearStart") LocalDate yearStart,
                       @Param("yearEnd") LocalDate yearEnd);

    @Query(
            value = """
                select r.member.id as memberId, sum(r.days) as totalDays
                from LeaveRequest r
                where r.company.id = :companyId
                    and r.member.id in :memberIds
                    and r.status = :status
                    and r.leaveType in :types
                    and r.startDate >= :yearStart
                    and r.endDate <= :yearEnd
                group by r.member.id
            """
    )
    List<PendingDaysSum> sumDaysByMembers(@Param("companyId") Long companyId,
                       @Param("memberIds") List<Long> memberIds,
                       @Param("status") LeaveRequestStatus status,
                       @Param("types") List<LeaveType> types,
                       @Param("yearStart") LocalDate yearStart,
                       @Param("yearEnd") LocalDate yearEnd);

    Optional<LeaveRequest> findByIdAndCompanyId(Long id, Long companyId);
}
