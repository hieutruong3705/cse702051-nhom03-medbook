package com.phenikaa.cse702051.medbook.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.phenikaa.cse702051.medbook.dto.slot.SlotHoldDTO;
import com.phenikaa.cse702051.medbook.service.SlotHoldService;
import lombok.RequiredArgsConstructor;

/** Giữ chỗ có thời hạn trên một khung giờ trong lúc người dùng hoàn tất đặt lịch (bệnh nhân, lễ tân). */
@RestController
@RequestMapping("/api/v1/appointment-slots")
@RequiredArgsConstructor
public class SlotHoldController {

    private final SlotHoldService slotHoldService;

    /** Giữ khung giờ cho người gọi. Người đang giữ gọi lại nhận đúng bản giữ cũ, không được gia hạn. */
    @PostMapping("/{slotId}/hold")
    public SlotHoldDTO hold(@PathVariable Long slotId) {
        return slotHoldService.hold(slotId);
    }

    /** Nhả bản giữ của chính người gọi; không có gì để nhả vẫn 204. */
    @DeleteMapping("/{slotId}/hold")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable Long slotId) {
        slotHoldService.release(slotId);
    }
}
