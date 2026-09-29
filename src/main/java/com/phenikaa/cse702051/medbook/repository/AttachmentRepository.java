package com.phenikaa.cse702051.medbook.repository;

import com.phenikaa.cse702051.medbook.model.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByEncounterId(Long encounterId);

    boolean existsByEncounterId(Long encounterId);

    List<Attachment> findByMimeType(String mimeType);
}