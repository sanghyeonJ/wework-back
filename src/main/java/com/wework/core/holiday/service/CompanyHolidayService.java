package com.wework.core.holiday.service;

import com.wework.core.company.entity.Company;
import com.wework.core.company.repository.CompanyRepository;
import com.wework.core.holiday.dto.CompanyHolidayRequest;
import com.wework.core.holiday.dto.CompanyHolidayResponse;
import com.wework.core.holiday.entity.CompanyHoliday;
import com.wework.core.holiday.error.HolidayErrorCode;
import com.wework.core.holiday.repository.CompanyHolidayRepository;
import com.wework.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CompanyHolidayService {

    private final CompanyHolidayRepository companyHolidayRepository;
    private final CompanyRepository companyRepository;

    public List<CompanyHolidayResponse> getHolidays(Long companyId, int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);

        List<CompanyHoliday> holidays = companyHolidayRepository.findAllInRange(companyId, from, to);
        return holidays.stream().map(CompanyHolidayResponse::of).toList();
    }

    @Transactional
    public CompanyHolidayResponse create(Long companyId, CompanyHolidayRequest request) {
        String name = request.getName().trim();

        if (companyHolidayRepository.existsByCompanyIdAndHolidayDate(companyId, request.getHolidayDate())) {
            throw new BusinessException(HolidayErrorCode.HOLIDAY_DATE_DUPLICATED);
        }

        Company company = companyRepository.getReferenceById(companyId);
        CompanyHoliday holiday = CompanyHoliday.create(company, request.getHolidayDate(), name, request.getRecurring());

        CompanyHoliday saved = companyHolidayRepository.save(holiday);
        return CompanyHolidayResponse.of(saved);
    }

    @Transactional
    public CompanyHolidayResponse update(Long companyId, Long id, CompanyHolidayRequest request) {
        CompanyHoliday holiday = companyHolidayRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new BusinessException(HolidayErrorCode.HOLIDAY_NOT_FOUND));
        if (companyHolidayRepository.existsByCompanyIdAndHolidayDateAndIdNot(companyId, request.getHolidayDate(), id)) {
            throw new BusinessException(HolidayErrorCode.HOLIDAY_DATE_DUPLICATED);
        }

        String name = request.getName().trim();
        holiday.update(request.getHolidayDate(), name, request.getRecurring());

        return CompanyHolidayResponse.of(holiday);
    }

    @Transactional
    public void delete(Long companyId, Long id) {
        CompanyHoliday holiday = companyHolidayRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new BusinessException(HolidayErrorCode.HOLIDAY_NOT_FOUND));
        companyHolidayRepository.delete(holiday);
    }

}
