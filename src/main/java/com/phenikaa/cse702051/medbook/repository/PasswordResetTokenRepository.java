package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.PasswordResetToken;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Đánh dấu đã dùng một cách nguyên tử: chỉ thành công (trả 1) với token chưa dùng và chưa
     * hết hạn, nên hai yêu cầu đồng thời cùng dùng một token chỉ có một yêu cầu thành công.
     */
    @Modifying
    @Query("""
            update PasswordResetToken t set t.usedAt = :now
            where t.id = :id and t.usedAt is null and t.expiresAt > :now
            """)
    int markUsed(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Vô hiệu hóa các token còn hiệu lực của người dùng khi họ yêu cầu token mới. */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int invalidateActive(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
