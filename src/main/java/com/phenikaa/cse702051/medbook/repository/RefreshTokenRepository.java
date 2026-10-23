package com.phenikaa.cse702051.medbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.phenikaa.cse702051.medbook.model.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
}
