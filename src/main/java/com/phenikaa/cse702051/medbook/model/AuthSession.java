package com.phenikaa.cse702051.medbook.model;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "auth_sessions", indexes = {
    @Index(name = "idx_auth_sessions_user", columnList = "user_id"),
    @Index(name = "idx_auth_sessions_expiry", columnList = "expires_at")
})
public class AuthSession {
    @Id @Column(length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "token_version", nullable = false) private int tokenVersion;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
}
