package com.phenikaa.cse702051.medbook.service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import com.phenikaa.cse702051.medbook.repository.MedicalServiceRepository;
import com.phenikaa.cse702051.medbook.dto.MedicalServiceDTO;

import org.springframework.stereotype.Service;

@Service
public class MedicalServiceService {
    private final MedicalServiceRepository repository;

    public MedicalServiceService(MedicalServiceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<MedicalServiceDTO> listActive() {
        return repository.findByStatus("ACTIVE").stream()
                .map(s -> new MedicalServiceDTO(
                        s.getId(), s.getCode(), s.getName(), s.getDescription(), s.getDurationMinutes(), s.getPrice()))
                .toList();
    }
}
