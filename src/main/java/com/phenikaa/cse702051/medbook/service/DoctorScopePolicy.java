package com.phenikaa.cse702051.medbook.service;

/**
 * Quy tắc "bác sĩ phụ trách bệnh nhân" dùng chung cho bệnh án, hồ sơ bệnh nhân, lần khám,
 * đơn thuốc, tệp và hóa đơn (YCCN-07).
 *
 * <p>Một bác sĩ phụ trách bệnh nhân khi có ít nhất một lịch hẹn không bị hủy, hoặc một lần
 * khám, giữa bác sĩ và bệnh nhân đó.
 */
public interface DoctorScopePolicy {

    boolean isResponsible(Long doctorId, Long patientId);
}
