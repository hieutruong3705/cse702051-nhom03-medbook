package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.SlotHold;

@Repository
public interface SlotHoldRepository extends JpaRepository<SlotHold, Long> {

    /** Dòng giữ chỗ của một khung giờ, kể cả đã hết hạn (người gọi tự so {@code expiresAt}). */
    Optional<SlotHold> findBySlotId(Long slotId);

    /** Các bản giữ còn hạn trên một nhóm khung giờ: một truy vấn cho cả danh sách giờ trống. */
    List<SlotHold> findBySlotIdInAndExpiresAtAfter(Collection<Long> slotIds, LocalDateTime now);

    /**
     * Khóa hàng của người dùng cho đến hết giao dịch, để hai yêu cầu giữ chỗ gần như đồng thời của cùng một người
     * (bấm liên tiếp hai khung giờ) chạy lần lượt và không để lại hai bản giữ.
     */
    @Query(value = "SELECT id FROM users WHERE id = :userId FOR UPDATE", nativeQuery = true)
    Long lockUser(@Param("userId") Long userId);

    @Modifying
    @Query("delete from SlotHold h where h.userId = :userId and h.slotId <> :slotId")
    int deleteOthersOfUser(@Param("userId") Long userId, @Param("slotId") Long slotId);

    @Modifying
    @Query("delete from SlotHold h where h.slotId = :slotId and h.userId = :userId")
    int deleteBySlotAndUser(@Param("slotId") Long slotId, @Param("userId") Long userId);

    @Modifying
    @Query("delete from SlotHold h where h.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
