package com.phenikaa.cse702051.medbook.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.service.AppointmentSearchService;

@RestController
@RequestMapping({"/api/v1/appointments", "/api/appointments"})
public class AppointmentSearchController {

    @Autowired
    private AppointmentSearchService searchService;

    @GetMapping("/search")
    public ResponseEntity<?> searchAppointments(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {

        // Dùng Page<?> để tránh lỗi ép kiểu generics giữa Service và Controller
        Page<?> results = searchService.search(status, page, size, sortBy, sortDirection);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", results.getContent(),
            "pagination", Map.of(
                "page", results.getNumber(),
                "size", results.getSize(),
                "total_elements", results.getTotalElements(),
                "total_pages", results.getTotalPages()
            )
        ));
    }
}