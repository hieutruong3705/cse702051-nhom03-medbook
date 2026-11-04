package com.phenikaa.cse702051.medbook.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.config.CacheConfig;
import com.phenikaa.cse702051.medbook.dto.clinic.ClinicInfoDTO;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorReviewRepository;
import com.phenikaa.cse702051.medbook.repository.MedicalServiceRepository;
import com.phenikaa.cse702051.medbook.repository.SpecialtyRepository;
import com.phenikaa.cse702051.medbook.util.CatalogRules;

/**
 * Thông tin giới thiệu phòng khám cho trang công khai (YCCN-37). MedBook phục vụ một phòng khám một cơ sở, nên
 * thông tin liên hệ là cấu hình chứ không phải một bảng. Kết quả được đệm ({@link CacheConfig#PUBLIC_CLINIC}) và bị
 * xóa sau khi danh mục, hồ sơ bác sĩ hoặc đánh giá thay đổi.
 */
@Service
public class ClinicService {

    private final DoctorRepository doctorRepository;
    private final SpecialtyRepository specialtyRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final DoctorReviewRepository reviewRepository;
    private final String name;
    private final String introduction;
    private final String address;
    private final String phone;
    private final String email;
    private final List<String> workingHours;

    public ClinicService(
            DoctorRepository doctorRepository,
            SpecialtyRepository specialtyRepository,
            MedicalServiceRepository medicalServiceRepository,
            DoctorReviewRepository reviewRepository,
            @Value("${medbook.clinic.name:Phòng khám MedBook}") String name,
            @Value("${medbook.clinic.introduction:}") String introduction,
            @Value("${medbook.clinic.address:}") String address,
            @Value("${medbook.clinic.phone:}") String phone,
            @Value("${medbook.clinic.email:}") String email,
            @Value("${medbook.clinic.working-hours:}") String workingHours) {
        this.doctorRepository = doctorRepository;
        this.specialtyRepository = specialtyRepository;
        this.medicalServiceRepository = medicalServiceRepository;
        this.reviewRepository = reviewRepository;
        this.name = name;
        this.introduction = introduction;
        this.address = address;
        this.phone = phone;
        this.email = email;
        // Nhiều dòng giờ làm việc phân tách bằng dấu chấm phẩy
        this.workingHours = Arrays.stream(workingHours.split(";")).map(String::trim).filter(s -> !s.isEmpty())
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.PUBLIC_CLINIC, key = "'info'")
    public ClinicInfoDTO getInfo() {
        Object[] rating = reviewRepository.summarizeVisible().stream().findFirst().orElse(new Object[] { null, 0L });
        return new ClinicInfoDTO(
                name,
                introduction,
                address,
                phone,
                email,
                workingHours,
                doctorRepository.countByIsActiveTrue(),
                specialtyRepository.countByStatus(CatalogRules.ACTIVE),
                medicalServiceRepository.countByStatus(CatalogRules.ACTIVE),
                ReviewService.round(rating[0]),
                rating[1] == null ? 0 : ((Number) rating[1]).longValue());
    }

    /** Mã các bác sĩ đang hoạt động và chuyên khoa đang dùng, cho bản đồ trang. */
    @Transactional(readOnly = true)
    public List<Long> activeDoctorIds() {
        return doctorRepository.findActiveIds();
    }

    @Transactional(readOnly = true)
    public List<Long> activeSpecialtyIds() {
        return specialtyRepository.findIdsByStatus(CatalogRules.ACTIVE);
    }
}
