package com.phenikaa.cse702051.medbook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentStatisticsDTO {
    private long totalAppointments;
    private long completedAppointments;
    private long cancelledAppointments;
    private double cancellationRate; // Tỷ lệ hủy (%)
}