package com.phenikaa.cse702051.medbook.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.AddBreakRequest;
import com.phenikaa.cse702051.medbook.dto.AppointmentSlotDTO;
import com.phenikaa.cse702051.medbook.dto.CreateScheduleRequest;
import com.phenikaa.cse702051.medbook.dto.DoctorScheduleDTO;
import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.ScheduleBreakDTO;
import com.phenikaa.cse702051.medbook.dto.UpdateScheduleRequest;
import com.phenikaa.cse702051.medbook.exception.ApiException;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.ErrorCode;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.DoctorSchedule;
import com.phenikaa.cse702051.medbook.model.ScheduleBreak;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorScheduleRepository;
import com.phenikaa.cse702051.medbook.repository.ScheduleBreakRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Ca làm việc của bác sĩ và các slot khám sinh từ ca (YCCN-14, 15).
 *
 * <ul>
 * <li>Bác sĩ luôn lấy từ JWT; mọi thao tác chỉ tác động lên ca của chính bác sĩ đó (người khác → 403).</li>
 * <li>Một ca chia thành các slot đều nhau từ giờ bắt đầu; slot chạm vào giờ nghỉ bị bỏ qua.</li>
 * <li>Hai ca của cùng một bác sĩ không được chồng giờ trong một ngày (422).</li>
 * <li>Sửa/xóa ca hoặc thêm giờ nghỉ chỉ được khi KHÔNG có lịch hẹn nào đang giữ chỗ trong phần bị ảnh hưởng
 * (409), nên không bao giờ làm mất lịch đã đặt.</li>
 * <li>Slot từng có lịch hẹn (kể cả đã hủy) không xóa cứng được vì khóa ngoại: nó được gỡ khỏi lịch bằng cách
 * đặt {@code status = CANCELLED}, {@code isAvailable = false} và giữ lại làm lịch sử.</li>
 * <li>Bảng ca không có cột độ dài slot (tránh đổi schema ở môi trường đã triển khai) nên {@code slotMinutes}
 * được suy ra từ chính các slot đã sinh.</li>
 * </ul>
 */
@Service
public class DoctorScheduleService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final LocalDate FAR_FUTURE = LocalDate.of(2999, 12, 31);
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_BOOKED = "BOOKED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final DoctorScheduleRepository scheduleRepository;
    private final ScheduleBreakRepository breakRepository;
    private final AppointmentSlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final CurrentUserService currentUserService;
    private final CurrentActorService currentActorService;

    public DoctorScheduleService(
            DoctorScheduleRepository scheduleRepository,
            ScheduleBreakRepository breakRepository,
            AppointmentSlotRepository slotRepository,
            AppointmentRepository appointmentRepository,
            DoctorRepository doctorRepository,
            CurrentUserService currentUserService,
            CurrentActorService currentActorService) {
        this.scheduleRepository = scheduleRepository;
        this.breakRepository = breakRepository;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.currentUserService = currentUserService;
        this.currentActorService = currentActorService;
    }

    // ================= Đọc =================

    /**
     * Ca của bác sĩ đang đăng nhập, theo ngày tăng dần. Mặc định chỉ từ hôm nay trở đi; truyền {@code from}
     * để xem cả các ca đã qua.
     */
    @Transactional(readOnly = true)
    public PageResponse<DoctorScheduleDTO> listMine(LocalDate from, LocalDate to, int page, int size) {
        Long doctorId = currentDoctorId();
        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : FAR_FUTURE;
        if (end.isBefore(start)) {
            throw new FieldValidationException("to", "Ngày kết thúc phải sau ngày bắt đầu");
        }

        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by("workDate").ascending().and(Sort.by("startTime").ascending()));
        Page<DoctorSchedule> result = scheduleRepository.findByDoctorIdAndWorkDateBetween(doctorId, start, end, pageable);
        if (result.isEmpty()) {
            return PageResponse.from(result, s -> toDTO(s, List.of(), List.of()));
        }

        List<Long> ids = result.getContent().stream().map(DoctorSchedule::getId).toList();
        Map<Long, List<ScheduleBreak>> breaksBySchedule = breakRepository.findByDoctorScheduleIdIn(ids).stream()
                .collect(Collectors.groupingBy(b -> b.getDoctorSchedule().getId()));
        LocalDate minDate = result.getContent().stream().map(DoctorSchedule::getWorkDate)
                .min(Comparator.naturalOrder()).orElse(start);
        LocalDate maxDate = result.getContent().stream().map(DoctorSchedule::getWorkDate)
                .max(Comparator.naturalOrder()).orElse(end);
        Map<LocalDate, List<AppointmentSlot>> slotsByDate = slotRepository
                .findByDoctorIdAndSlotDateBetween(doctorId, minDate, maxDate).stream()
                .collect(Collectors.groupingBy(AppointmentSlot::getSlotDate));

        return PageResponse.from(result, s -> toDTO(s,
                breaksBySchedule.getOrDefault(s.getId(), List.of()),
                slotsOf(s, slotsByDate.getOrDefault(s.getWorkDate(), List.of()))));
    }

    @Transactional(readOnly = true)
    public DoctorScheduleDTO getMine(Long scheduleId) {
        DoctorSchedule schedule = requireOwnedSchedule(scheduleId);
        return toDTO(schedule);
    }

    /** Mọi slot (trống/đã đặt) của bác sĩ đang đăng nhập trong một ngày; slot đã gỡ khỏi lịch không hiện. */
    @Transactional(readOnly = true)
    public List<AppointmentSlotDTO> listMySlots(LocalDate date) {
        Long doctorId = currentDoctorId();
        LocalDate day = date != null ? date : LocalDate.now();
        return slotRepository.findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctorId, day).stream()
                .filter(slot -> !isRetired(slot))
                .map(slot -> new AppointmentSlotDTO(slot.getId(), doctorId, slot.getSlotDate(), slot.getStartTime(),
                        slot.getEndTime(), slot.getStatus()))
                .toList();
    }

    // ================= Tạo / sửa / xóa ca =================

    /** YCCN-14: tạo ca và tự sinh slot, bỏ qua các khoảng giờ nghỉ. */
    @Transactional
    public DoctorScheduleDTO create(CreateScheduleRequest request) {
        Long doctorId = currentDoctorId();
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ bác sĩ!"));
        if (!Boolean.TRUE.equals(doctor.getIsActive())) {
            throw new ForbiddenException("Hồ sơ bác sĩ đã ngừng hoạt động nên không thể tạo ca làm việc!");
        }

        LocalDate workDate = request.workDate();
        if (workDate.isBefore(LocalDate.now())) {
            throw new FieldValidationException("workDate", "Không thể tạo ca cho ngày đã qua");
        }
        validateShift(request.startTime(), request.endTime(), request.slotMinutes());
        List<CreateScheduleRequest.BreakInput> breakInputs =
                request.breaks() == null ? List.of() : request.breaks();
        for (CreateScheduleRequest.BreakInput input : breakInputs) {
            validateBreak(input.startTime(), input.endTime(), request.startTime(), request.endTime());
        }
        assertNoOverlap(doctorId, workDate, request.startTime(), request.endTime(), null);

        DoctorSchedule schedule = scheduleRepository.save(DoctorSchedule.builder()
                .doctor(doctor)
                .workDate(workDate)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .build());

        List<ScheduleBreak> breaks = new ArrayList<>();
        for (CreateScheduleRequest.BreakInput input : breakInputs) {
            breaks.add(breakRepository.save(ScheduleBreak.builder()
                    .doctorSchedule(schedule)
                    .startTime(input.startTime())
                    .endTime(input.endTime())
                    .reason(blankToNull(input.reason()))
                    .build()));
        }

        List<AppointmentSlot> slots = generateSlots(doctor, schedule, request.slotMinutes(), breaks);
        return toDTO(schedule, breaks, slots);
    }

    /** Sửa giờ ca: gỡ slot cũ (chưa ai đặt) rồi sinh lại theo giờ mới. Có lịch đã đặt trong ca → 409. */
    @Transactional
    public DoctorScheduleDTO update(Long scheduleId, UpdateScheduleRequest request) {
        DoctorSchedule schedule = requireOwnedSchedule(scheduleId);
        if (schedule.getWorkDate().isBefore(LocalDate.now())) {
            throw new ConflictException("Không thể sửa ca của ngày đã qua!");
        }

        List<AppointmentSlot> current = slotsOf(schedule);
        LocalTime start = request.startTime() != null ? request.startTime() : schedule.getStartTime();
        LocalTime end = request.endTime() != null ? request.endTime() : schedule.getEndTime();
        Integer minutes = request.slotMinutes() != null ? request.slotMinutes() : deriveSlotMinutes(current);
        if (minutes == null) {
            throw new FieldValidationException("slotMinutes",
                    "Ca này không còn slot nào để suy ra độ dài, hãy nhập số phút mỗi slot");
        }
        validateShift(start, end, minutes);

        List<ScheduleBreak> breaks = breakRepository.findByDoctorScheduleId(schedule.getId());
        for (ScheduleBreak b : breaks) {
            if (b.getStartTime().isBefore(start) || b.getEndTime().isAfter(end)) {
                throw new FieldValidationException("startTime",
                        "Giờ nghỉ " + b.getStartTime() + "–" + b.getEndTime()
                                + " nằm ngoài giờ mới của ca. Hãy xóa giờ nghỉ đó trước.");
            }
        }
        assertNoOverlap(schedule.getDoctor().getId(), schedule.getWorkDate(), start, end, schedule.getId());
        assertNoActiveAppointments(current, "sửa ca");

        clearSlots(current);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule = scheduleRepository.save(schedule);

        List<AppointmentSlot> slots = generateSlots(schedule.getDoctor(), schedule, minutes, breaks);
        return toDTO(schedule, breaks, slots);
    }

    /** Xóa ca cùng slot chưa đặt. Có lịch đã đặt trong ca → 409, ca giữ nguyên. */
    @Transactional
    public void delete(Long scheduleId) {
        DoctorSchedule schedule = requireOwnedSchedule(scheduleId);
        List<AppointmentSlot> slots = slotsOf(schedule);
        assertNoActiveAppointments(slots, "xóa ca");

        clearSlots(slots);
        breakRepository.deleteAll(breakRepository.findByDoctorScheduleId(schedule.getId()));
        scheduleRepository.delete(schedule);
    }

    // ================= Giờ nghỉ =================

    /** Thêm giờ nghỉ: gỡ các slot trống bị chạm; chạm slot đã đặt → 409. */
    @Transactional
    public DoctorScheduleDTO addBreak(Long scheduleId, AddBreakRequest request) {
        DoctorSchedule schedule = requireOwnedSchedule(scheduleId);
        validateBreak(request.startTime(), request.endTime(), schedule.getStartTime(), schedule.getEndTime());

        List<AppointmentSlot> affected = slotsOf(schedule).stream()
                .filter(slot -> !isRetired(slot))
                .filter(slot -> overlaps(slot.getStartTime(), slot.getEndTime(), request.startTime(), request.endTime()))
                .toList();
        assertNoActiveAppointments(affected, "thêm giờ nghỉ");

        clearSlots(affected);
        breakRepository.save(ScheduleBreak.builder()
                .doctorSchedule(schedule)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .reason(blankToNull(request.reason()))
                .build());
        return toDTO(schedule);
    }

    /** Xóa giờ nghỉ: sinh lại các slot trống từng bị loại vì giờ nghỉ này (nếu không chạm giờ nghỉ khác). */
    @Transactional
    public DoctorScheduleDTO removeBreak(Long scheduleId, Long breakId) {
        DoctorSchedule schedule = requireOwnedSchedule(scheduleId);
        ScheduleBreak removed = breakRepository.findById(breakId)
                .filter(b -> b.getDoctorSchedule().getId().equals(schedule.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giờ nghỉ trong ca này!"));

        List<AppointmentSlot> existing = slotsOf(schedule);
        Integer minutes = deriveSlotMinutes(existing);
        if (minutes == null) {
            throw new ConflictException("Không xác định được độ dài slot của ca này. "
                    + "Hãy xóa ca và tạo lại để thay đổi giờ nghỉ!");
        }

        breakRepository.delete(removed);
        List<ScheduleBreak> remaining = breakRepository.findByDoctorScheduleId(schedule.getId()).stream()
                .filter(b -> !b.getId().equals(removed.getId()))
                .toList();

        // Chỉ thêm các ô lưới nằm trong giờ nghỉ vừa xóa, không chạm giờ nghỉ còn lại, và chưa có slot đang hoạt động
        List<AppointmentSlot> restored = new ArrayList<>();
        LocalTime cursor = schedule.getStartTime();
        while (true) {
            LocalTime slotStart = cursor;
            LocalTime slotEnd = cursor.plusMinutes(minutes);
            if (!slotEnd.isAfter(slotStart) || slotEnd.isAfter(schedule.getEndTime())) {
                break; // hết ca, hoặc cộng giờ bị tràn qua nửa đêm
            }
            boolean inRemovedBreak = overlaps(slotStart, slotEnd, removed.getStartTime(), removed.getEndTime());
            boolean inOtherBreak = remaining.stream()
                    .anyMatch(b -> overlaps(slotStart, slotEnd, b.getStartTime(), b.getEndTime()));
            boolean exists = existing.stream()
                    .anyMatch(slot -> !isRetired(slot) && slot.getStartTime().equals(slotStart));
            if (inRemovedBreak && !inOtherBreak && !exists) {
                restored.add(newSlot(schedule.getDoctor(), schedule.getWorkDate(), slotStart, slotEnd));
            }
            cursor = slotEnd;
        }
        List<AppointmentSlot> all = new ArrayList<>(existing);
        all.addAll(slotRepository.saveAll(restored));
        return toDTO(schedule, remaining, all);
    }

    // ================= Quy tắc nghiệp vụ =================

    private Long currentDoctorId() {
        currentUserService.requireRole("DOCTOR");
        return currentActorService.requireCurrentDoctorId();
    }

    private DoctorSchedule requireOwnedSchedule(Long scheduleId) {
        Long doctorId = currentDoctorId();
        DoctorSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc!"));
        if (!schedule.getDoctor().getId().equals(doctorId)) {
            throw new ForbiddenException("Ca làm việc này không thuộc về bạn!");
        }
        return schedule;
    }

    private void validateShift(LocalTime start, LocalTime end, int slotMinutes) {
        if (!start.isBefore(end)) {
            throw new FieldValidationException("endTime", "Giờ kết thúc phải sau giờ bắt đầu");
        }
        if (Duration.between(start, end).toMinutes() < slotMinutes) {
            throw new FieldValidationException("slotMinutes", "Ca làm việc ngắn hơn một slot");
        }
    }

    private void validateBreak(LocalTime breakStart, LocalTime breakEnd, LocalTime shiftStart, LocalTime shiftEnd) {
        if (!breakStart.isBefore(breakEnd)) {
            throw new FieldValidationException("breaks", "Giờ nghỉ phải có giờ kết thúc sau giờ bắt đầu");
        }
        if (breakStart.isBefore(shiftStart) || breakEnd.isAfter(shiftEnd)) {
            throw new FieldValidationException("breaks", "Giờ nghỉ phải nằm trong ca làm việc");
        }
    }

    /** Hai ca của một bác sĩ không được chồng giờ trong cùng ngày → 422. */
    private void assertNoOverlap(Long doctorId, LocalDate date, LocalTime start, LocalTime end, Long ignoreScheduleId) {
        Optional<DoctorSchedule> clash = scheduleRepository.findByDoctorIdAndWorkDate(doctorId, date).stream()
                .filter(other -> !other.getId().equals(ignoreScheduleId))
                .filter(other -> overlaps(start, end, other.getStartTime(), other.getEndTime()))
                .findFirst();
        if (clash.isPresent()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE_ENTITY, "Ca làm việc chồng giờ với ca "
                    + clash.get().getStartTime() + "–" + clash.get().getEndTime() + " đã có trong ngày này!");
        }
    }

    /** Có slot nào đang được lịch hẹn giữ chỗ (BOOKED/IN_PROGRESS/COMPLETED) thì không được thay đổi → 409. */
    private void assertNoActiveAppointments(Collection<AppointmentSlot> slots, String action) {
        boolean hasActive = slots.stream()
                .anyMatch(slot -> appointmentRepository.countBySlotIdAndStatusNot(slot.getId(),
                        AppointmentStatus.CANCELLED) > 0);
        if (hasActive) {
            throw new ConflictException("Không thể " + action
                    + " vì đã có lịch hẹn được đặt trong khoảng giờ này. Hãy xử lý các lịch hẹn đó trước!");
        }
    }

    /** Xóa cứng slot chưa từng có lịch hẹn; slot có lịch sử chỉ được gỡ khỏi lịch (CANCELLED). */
    private void clearSlots(Collection<AppointmentSlot> slots) {
        for (AppointmentSlot slot : slots) {
            if (appointmentRepository.existsAnyBySlotId(slot.getId())) {
                slot.setIsAvailable(false);
                slot.setStatus(STATUS_CANCELLED);
                slotRepository.save(slot);
            } else {
                slotRepository.delete(slot);
            }
        }
    }

    private List<AppointmentSlot> generateSlots(
            Doctor doctor, DoctorSchedule schedule, int slotMinutes, List<ScheduleBreak> breaks) {
        List<AppointmentSlot> generated = new ArrayList<>();
        LocalTime cursor = schedule.getStartTime();
        while (true) {
            LocalTime slotStart = cursor;
            LocalTime slotEnd = cursor.plusMinutes(slotMinutes);
            if (!slotEnd.isAfter(slotStart) || slotEnd.isAfter(schedule.getEndTime())) {
                break; // hết ca, hoặc cộng giờ bị tràn qua nửa đêm
            }
            boolean onBreak = breaks.stream()
                    .anyMatch(b -> overlaps(slotStart, slotEnd, b.getStartTime(), b.getEndTime()));
            if (!onBreak) {
                generated.add(newSlot(doctor, schedule.getWorkDate(), slotStart, slotEnd));
            }
            cursor = slotEnd;
        }
        return slotRepository.saveAll(generated);
    }

    private static AppointmentSlot newSlot(Doctor doctor, LocalDate date, LocalTime start, LocalTime end) {
        return AppointmentSlot.builder()
                .doctor(doctor)
                .slotDate(date)
                .startTime(start)
                .endTime(end)
                .isAvailable(true)
                .status(STATUS_AVAILABLE)
                .build();
    }

    // ================= Dựng DTO =================

    private DoctorScheduleDTO toDTO(DoctorSchedule schedule) {
        List<ScheduleBreak> breaks = breakRepository.findByDoctorScheduleId(schedule.getId());
        return toDTO(schedule, breaks, slotsOf(schedule));
    }

    private DoctorScheduleDTO toDTO(DoctorSchedule schedule, List<ScheduleBreak> breaks, List<AppointmentSlot> slots) {
        List<AppointmentSlot> live = slots.stream().filter(slot -> !isRetired(slot)).toList();
        int booked = (int) live.stream().filter(slot -> STATUS_BOOKED.equalsIgnoreCase(slot.getStatus())).count();
        List<ScheduleBreakDTO> breakDtos = breaks.stream()
                .sorted(Comparator.comparing(ScheduleBreak::getStartTime))
                .map(b -> new ScheduleBreakDTO(b.getId(), b.getStartTime(), b.getEndTime(), b.getReason()))
                .toList();
        return new DoctorScheduleDTO(
                schedule.getId(),
                schedule.getDoctor().getId(),
                schedule.getWorkDate(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                deriveSlotMinutes(slots),
                breakDtos,
                live.size(),
                booked);
    }

    /** Các slot của ca: cùng bác sĩ, cùng ngày, nằm trọn trong giờ của ca (ca không chồng nhau nên không lẫn). */
    private List<AppointmentSlot> slotsOf(DoctorSchedule schedule) {
        return slotsOf(schedule, slotRepository.findByDoctorIdAndSlotDateOrderByStartTimeAsc(
                schedule.getDoctor().getId(), schedule.getWorkDate()));
    }

    private static List<AppointmentSlot> slotsOf(DoctorSchedule schedule, Collection<AppointmentSlot> daySlots) {
        return daySlots.stream()
                .filter(slot -> !slot.getStartTime().isBefore(schedule.getStartTime())
                        && !slot.getEndTime().isAfter(schedule.getEndTime()))
                .sorted(Comparator.comparing(AppointmentSlot::getStartTime))
                .toList();
    }

    /** Độ dài slot ngắn nhất trong ca; ưu tiên slot đang hoạt động, rồi đến slot đã gỡ; không có slot → null. */
    private static Integer deriveSlotMinutes(Collection<AppointmentSlot> slots) {
        return slots.stream()
                .sorted(Comparator.comparing(AppointmentSlot::getStartTime))
                .filter(slot -> !isRetired(slot))
                .map(slot -> (int) Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes())
                .min(Comparator.naturalOrder())
                .or(() -> slots.stream()
                        .map(slot -> (int) Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes())
                        .min(Comparator.naturalOrder()))
                .orElse(null);
    }

    private static boolean isRetired(AppointmentSlot slot) {
        return STATUS_CANCELLED.equalsIgnoreCase(slot.getStatus());
    }

    /** Hai khoảng [aStart, aEnd) và [bStart, bEnd) có chồng nhau không. */
    private static boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
