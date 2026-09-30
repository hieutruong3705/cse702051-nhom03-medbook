package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.repository.DataMetricsRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.InvoiceRepository;
import com.phenikaa.cse702051.medbook.model.Invoice;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DataMetricsService {

    private final DataMetricsRepository dataMetricsRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final InvoiceRepository invoiceRepository;

    public DataMetricsService(
            DataMetricsRepository dataMetricsRepository,
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            InvoiceRepository invoiceRepository
    ) {
        this.dataMetricsRepository = dataMetricsRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.invoiceRepository = invoiceRepository;
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        metrics.put(
                "totalTables",
                dataMetricsRepository.countTables()
        );

        metrics.put(
                "totalIndexes",
                dataMetricsRepository.countIndexes()
        );

        metrics.put(
                "totalForeignKeys",
                dataMetricsRepository.countForeignKeys()
        );

        metrics.put(
                "hotQueryMedianMs",
                dataMetricsRepository.measureHotQueryMedianMs()
        );

        long totalPatients = patientRepository.count();
        long totalAppointments = appointmentRepository.count();
        BigDecimal totalRevenue = invoiceRepository.findAll().stream()
                .filter(i -> "PAID".equals(i.getStatus()))
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        metrics.put("totalPatients", totalPatients);
        metrics.put("totalAppointments", totalAppointments);
        metrics.put("totalRevenue", totalRevenue);

        return metrics;
    }
}