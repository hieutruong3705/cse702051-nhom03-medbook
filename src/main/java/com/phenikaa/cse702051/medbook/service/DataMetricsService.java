package com.phenikaa.cse702051.medbook.service;

import com.phenikaa.cse702051.medbook.repository.DataMetricsRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DataMetricsService {

    private final DataMetricsRepository dataMetricsRepository;

    public DataMetricsService(
            DataMetricsRepository dataMetricsRepository
    ) {
        this.dataMetricsRepository = dataMetricsRepository;
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

        return metrics;
    }
}