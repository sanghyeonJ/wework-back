package com.wework.core.company.entity;

import com.wework.global.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "company")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseEntity {

    private static final String DEFAULT_COUNTRY_CODE = "KR";
    private static final String DEFAULT_TIME_ZONE = "Asia/Seoul";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "business_number", nullable = false, unique = true, length = 20)
    private String businessNumber;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyStatus status;

    private Company(String name, String businessNumber, String countryCode, String timeZone) {
        this.name = name;
        this.businessNumber = businessNumber.trim();
        this.countryCode = (countryCode != null) ? countryCode : DEFAULT_COUNTRY_CODE;
        this.timeZone = (timeZone != null) ? timeZone : DEFAULT_TIME_ZONE;
        this.status = CompanyStatus.ACTIVE;
    }

    public static Company create(String name, String businessNumber, String countryCode, String timeZone) {
        return new Company(name, businessNumber, countryCode, timeZone);
    }

}
