package com.phenikaa.cse702051.medbook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.clinic.ClinicInfoDTO;
import com.phenikaa.cse702051.medbook.service.ClinicService;

/** Giới thiệu phòng khám trên trang công khai (YCCN-37), không cần đăng nhập. */
@RestController
@RequestMapping("/api/v1/clinic")
public class ClinicController {

    private final ClinicService clinicService;

    public ClinicController(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    @GetMapping
    public ClinicInfoDTO get() {
        return clinicService.getInfo();
    }
}
