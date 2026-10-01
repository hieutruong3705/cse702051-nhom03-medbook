package com.phenikaa.cse702051.medbook.model;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_tokens_session", columnList = "session_id"))
public class RefreshToken {
    @Id @Column(name = "token_hash", length = 64, columnDefinition = "char(64)") private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false) private AuthSession session;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "used_at") private Instant usedAt;
}
