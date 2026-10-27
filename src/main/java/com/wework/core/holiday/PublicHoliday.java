package com.wework.core.holiday;

import com.wework.global.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "public_holiday",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_public_holiday_country_date_name",
                columnNames = {"country_code", "holiday_date", "name"}
        )
)
public class PublicHoliday extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false, length = 100)
    private String name;

    private PublicHoliday(String countryCode, LocalDate holidayDate, String name) {
        this.countryCode = countryCode;
        this.holidayDate = holidayDate;
        this.name = name;
    }

    public static PublicHoliday create(String countryCode, LocalDate holidayDate, String name) {
        return new PublicHoliday(countryCode, holidayDate, name);
    }

}
