package com.phenikaa.cse702051.medbook.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import com.phenikaa.cse702051.medbook.service.MedicalServiceService;
import com.phenikaa.cse702051.medbook.dto.MedicalServiceDTO;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/medical-services")
public class MedicalServiceController {
    private final MedicalServiceService service;

    public MedicalServiceController(MedicalServiceService service) {
        this.service = service;
    }

    @GetMapping
    public List<MedicalServiceDTO> list() {
        return service.listActive();
    }
}
