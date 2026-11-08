package com.wework.core.leave.repository;

import java.math.BigDecimal;

public interface PendingDaysSum {

    Long getMemberId();

    BigDecimal getTotalDays();

}
