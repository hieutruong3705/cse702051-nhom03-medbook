package com.phenikaa.cse702051.medbook.controller;

import com.phenikaa.cse702051.medbook.service.DataMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/data-metrics")
public class DataMetricsController {

    private final DataMetricsService dataMetricsService;

    public DataMetricsController(
            DataMetricsService dataMetricsService
    ) {
        this.dataMetricsService = dataMetricsService;
    }

    @GetMapping
    public Map<String, Object> getMetrics() {
        return dataMetricsService.getMetrics();
    }
}