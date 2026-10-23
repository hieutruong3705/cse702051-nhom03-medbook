package com.phenikaa.cse702051.medbook.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.phenikaa.cse702051.medbook.model.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from AuthSession s where s.id = :id")
    java.util.Optional<AuthSession> findLocked(@org.springframework.data.repository.query.Param("id") String id);
}
