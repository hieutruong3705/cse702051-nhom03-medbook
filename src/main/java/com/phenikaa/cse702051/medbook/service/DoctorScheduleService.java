package com.phenikaa.cse702051.medbook.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.AddBreakRequest;
import com.phenikaa.cse702051.medbook.dto.AppointmentSlotDTO;
import com.phenikaa.cse702051.medbook.dto.CreateScheduleRequest;
import com.phenikaa.cse702051.medbook.dto.DoctorScheduleDTO;
import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.ScheduleBreakDTO;
import com.phenikaa.cse702051.medbook.dto.UpdateScheduleRequest;
import com.phenikaa.cse702051.medbook.dto.schedule.ScheduleSlotDTO;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.FieldValidationException;
import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.exception.UnprocessableEntityException;
import com.phenikaa.cse702051.medbook.model.AppointmentSlot;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.DoctorSchedule;
import com.phenikaa.cse702051.medbook.model.ScheduleBreak;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.AppointmentSlotRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorDayOffRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorScheduleRepository;
import com.phenikaa.cse702051.medbook.repository.ScheduleBreakRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;

/**
 * Ca làm việc của bác sĩ và các slot khám sinh từ ca (YCCN-14, 15).
 *
 * <ul>
 * <li>Bác sĩ luôn lấy từ JWT; mọi thao tác chỉ tác động lên ca của chính bác sĩ đó (người khác → 403).</li>
 * <li>Mọi thay đổi (tạo, sửa, xóa ca, giờ nghỉ, ngày nghỉ) bắt đầu bằng việc khóa dòng bác sĩ
 * ({@link #lockCurrentDoctor()}), nên các yêu cầu song song của cùng một bác sĩ chạy lần lượt và không thể cùng
 * vượt qua kiểm tra chồng ca. Giao dịch dùng mức cô lập READ_COMMITTED để yêu cầu đến sau nhìn thấy dữ liệu mà
 * yêu cầu trước vừa ghi.</li>
 * <li>{@link #rebuildSlots} là đường duy nhất sinh và gỡ slot: một ca chia thành các slot đều nhau từ giờ bắt
 * đầu, bỏ ô chạm giờ nghỉ, không còn ô nào nếu là ngày nghỉ.</li>
 * <li>Hai ca của cùng một bác sĩ không được chồng giờ trong một ngày (422).</li>
 * <li>Thay đổi nào chạm vào slot đang có lịch hẹn giữ chỗ đều bị từ chối (409), nên không bao giờ làm mất lịch
 * đã đặt. Slot từng có lịch hẹn (kể cả đã hủy) không xóa cứng mà được giữ ở trạng thái {@code CANCELLED}
 * (xem {@link SlotRetirement}).</li>
 * </ul>
 */
@Service
public class DoctorScheduleService {

    static final int MAX_SLOT_MINUTES = 120;
    static final int SLOT_MINUTES_STEP = 5;
    static final int MAX_SHIFT_HOURS = 12;
    static final int MAX_SLOTS_PER_SHIFT = 100;
    static final int MAX_SHIFTS_PER_DAY = 4;
    static final int MAX_BREAKS_PER_SHIFT = 10;
    static final int MAX_LIST_DAYS = 62;

    private static final int MAX_PAGE_SIZE = 100;
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_BOOKED = "BOOKED";
    private static final String STATUS_CANCELLED = SlotRetirement.STATUS_CANCELLED;

    private final DoctorScheduleRepository scheduleRepository;
    private final ScheduleBreakRepository breakRepository;
    private final DoctorDayOffRepository dayOffRepository;
    private final AppointmentSlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final SlotRetirement slotRetirement;
    private final CurrentUserService currentUserService;
    private final CurrentActorService currentActorService;
    private final AuditLogService auditLogService;

    public DoctorScheduleService(
            DoctorScheduleRepository scheduleRepository,
            ScheduleBreakRepository breakRepository,
            DoctorDayOffRepository dayOffRepository,
            AppointmentSlotRepository slotRepository,
            AppointmentRepository appointmentRepository,
            DoctorRepository doctorRepository,
            SlotRetirement slotRetirement,
            CurrentUserService currentUserService,
            CurrentActorService currentActorService,
            AuditLogService auditLogService) {
        this.scheduleRepository = scheduleRepository;
        this.breakRepository = breakRepository;
        this.dayOffRepository = dayOffRepository;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.slotRetirement = slotRetirement;
        this.currentUserService = currentUserService;
        this.currentActorService = currentActorService;
        this.auditLogService = auditLogService;
    }

    /** Một ô của lưới slot: [start, end). */
    private record Cell(LocalTime start, LocalTime end) {

        static Cell of(AppointmentSlot slot) {
            return new Cell(slot.getStartTime(), slot.getEndTime());
        }
    }

    // ================= Đọc =================

    /**
     * Ca của bác sĩ đang đăng nhập, theo ngày tăng dần. Mặc định từ hôm nay; truyền {@code from} để xem cả các ca
     * đã qua. Thiếu {@code to} thì lấy đủ {@value #MAX_LIST_DAYS} ngày kể từ {@code from}; khoảng dài hơn → 400.
     */
    @Transactional(readOnly = true)
    public PageResponse<DoctorScheduleDTO> listMine(LocalDate from, LocalDate to, int page, int size) {
        Long doctorId = currentDoctorId();
        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start.plusDays(MAX_LIST_DAYS - 1L);
        if (end.isBefore(start)) {
            throw new FieldValidationException("to", "Ngày kết thúc phải sau ngày bắt đầu");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_LIST_DAYS) {
            throw new FieldValidationException("to", "Chỉ xem được tối đa " + MAX_LIST_DAYS + " ngày mỗi lần");
        }

        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by("workDate").ascending().and(Sort.by("startTime").ascending())
                        .and(Sort.by("id").ascending()));
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
        return toDTO(requireOwnedSchedule(scheduleId, currentDoctorId()));
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

    /** Slot của một ca kèm lịch hẹn đang giữ chỗ (không có thông tin bệnh nhân); slot đã gỡ không hiện. */
    @Transactional(readOnly = true)
    public List<ScheduleSlotDTO> listSlotsOfSchedule(Long scheduleId) {
        DoctorSchedule schedule = requireOwnedSchedule(scheduleId, currentDoctorId());
        List<AppointmentSlot> live = liveSlots(slotsOf(schedule));
        Map<Long, Long> appointmentBySlot = activeAppointments(live);
        return live.stream()
                .map(slot -> new ScheduleSlotDTO(slot.getId(), slot.getStartTime(), slot.getEndTime(),
                        slot.getStatus(), appointmentBySlot.get(slot.getId())))
                .toList();
    }

    // ================= Tạo / sửa / xóa ca =================

    /** YCCN-14: tạo ca và tự sinh slot, bỏ qua các khoảng giờ nghỉ. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DoctorScheduleDTO create(CreateScheduleRequest request) {
        Doctor doctor = lockCurrentDoctor();
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
        if (dayOffRepository.existsByDoctorIdAndOffDate(doctor.getId(), workDate)) {
            throw new UnprocessableEntityException("Bạn đã đăng ký nghỉ ngày " + workDate
                    + ". Hãy xóa ngày nghỉ trước khi tạo ca làm việc!");
        }
        List<DoctorSchedule> sameDay = scheduleRepository.findByDoctorIdAndWorkDate(doctor.getId(), workDate);
        assertNoOverlap(sameDay, request.startTime(), request.endTime(), null);
        if (sameDay.size() >= MAX_SHIFTS_PER_DAY) {
            throw new FieldValidationException("workDate", "Mỗi ngày tối đa " + MAX_SHIFTS_PER_DAY + " ca làm việc");
        }

        DoctorSchedule schedule = scheduleRepository.save(DoctorSchedule.builder()
                .doctor(doctor)
                .workDate(workDate)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .slotMinutes(request.slotMinutes())
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

        int slots = rebuildSlots(schedule, breaks, List.of());
        audit(AuditActions.SCHEDULE_CREATE, schedule, slots);
        return toDTO(schedule);
    }

    /** Sửa giờ ca và sinh lại slot theo giờ mới. Có lịch đã đặt trong ca → 409, ca giữ nguyên. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DoctorScheduleDTO update(Long scheduleId, UpdateScheduleRequest request) {
        Doctor doctor = lockCurrentDoctor();
        DoctorSchedule schedule = requireOwnedSchedule(scheduleId, doctor.getId());
        if (schedule.getWorkDate().isBefore(LocalDate.now())) {
            throw new ConflictException("Không thể sửa ca của ngày đã qua!");
        }

        List<AppointmentSlot> current = slotsOf(schedule); // theo giờ CŨ của ca
        LocalTime start = request.startTime() != null ? request.startTime() : schedule.getStartTime();
        LocalTime end = request.endTime() != null ? request.endTime() : schedule.getEndTime();
        Integer minutes = request.slotMinutes() != null ? request.slotMinutes() : slotMinutesOf(schedule, current);
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
        assertNoOverlap(scheduleRepository.findByDoctorIdAndWorkDate(doctor.getId(), schedule.getWorkDate()),
                start, end, schedule.getId());
        assertNoActiveAppointments(liveSlots(current), "sửa ca");

        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setSlotMinutes(minutes);
        schedule = scheduleRepository.save(schedule);

        int slots = rebuildSlots(schedule, breaks, current);
        audit(AuditActions.SCHEDULE_UPDATE, schedule, slots);
        return toDTO(schedule);
    }

    /** Xóa ca cùng slot chưa đặt. Có lịch đã đặt trong ca → 409, ca giữ nguyên. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long scheduleId) {
        Doctor doctor = lockCurrentDoctor();
        DoctorSchedule schedule = requireOwnedSchedule(scheduleId, doctor.getId());
        List<AppointmentSlot> live = liveSlots(slotsOf(schedule));
        assertNoActiveAppointments(live, "xóa ca");

        syncSlots(schedule, live, List.of());
        breakRepository.deleteAll(breakRepository.findByDoctorScheduleId(schedule.getId()));
        scheduleRepository.delete(schedule);
        flushOrConflict();
        audit(AuditActions.SCHEDULE_DELETE, schedule, live.size());
    }

    // ================= Giờ nghỉ =================

    /** Thêm giờ nghỉ: gỡ các slot trống bị chạm; chạm slot đã đặt → 409. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DoctorScheduleDTO addBreak(Long scheduleId, AddBreakRequest request) {
        Doctor doctor = lockCurrentDoctor();
        DoctorSchedule schedule = requireOwnedSchedule(scheduleId, doctor.getId());
        validateBreak(request.startTime(), request.endTime(), schedule.getStartTime(), schedule.getEndTime());
        List<ScheduleBreak> breaks = new ArrayList<>(breakRepository.findByDoctorScheduleId(schedule.getId()));
        if (breaks.size() >= MAX_BREAKS_PER_SHIFT) {
            throw new FieldValidationException("breaks", "Tối đa " + MAX_BREAKS_PER_SHIFT + " giờ nghỉ mỗi ca");
        }

        List<AppointmentSlot> current = slotsOf(schedule);
        List<AppointmentSlot> affected = liveSlots(current).stream()
                .filter(slot -> overlaps(slot.getStartTime(), slot.getEndTime(), request.startTime(), request.endTime()))
                .toList();
        assertNoActiveAppointments(affected, "thêm giờ nghỉ");

        breaks.add(breakRepository.save(ScheduleBreak.builder()
                .doctorSchedule(schedule)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .reason(blankToNull(request.reason()))
                .build()));
        int slots = rebuildSlots(schedule, breaks, current);
        audit(AuditActions.SCHEDULE_UPDATE, schedule, slots);
        return toDTO(schedule);
    }

    /** Xóa giờ nghỉ: sinh lại các slot trống từng bị loại vì giờ nghỉ này (nếu không chạm giờ nghỉ khác). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DoctorScheduleDTO removeBreak(Long scheduleId, Long breakId) {
        Doctor doctor = lockCurrentDoctor();
        DoctorSchedule schedule = requireOwnedSchedule(scheduleId, doctor.getId());
        ScheduleBreak removed = breakRepository.findById(breakId)
                .filter(b -> b.getDoctorSchedule().getId().equals(schedule.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giờ nghỉ trong ca này!"));

        List<AppointmentSlot> current = slotsOf(schedule);
        if (slotMinutesOf(schedule, current) == null) {
            throw new ConflictException("Không xác định được độ dài slot của ca này. "
                    + "Hãy xóa ca và tạo lại để thay đổi giờ nghỉ!");
        }
        List<ScheduleBreak> remaining = breakRepository.findByDoctorScheduleId(schedule.getId()).stream()
                .filter(b -> !b.getId().equals(removed.getId()))
                .toList();
        breakRepository.delete(removed);

        int slots = rebuildSlots(schedule, remaining, current);
        audit(AuditActions.SCHEDULE_UPDATE, schedule, slots);
        return toDTO(schedule);
    }

    // ================= Sinh và gỡ slot =================

    /**
     * Đưa các slot của ca về đúng trạng thái mà giờ ca, giờ nghỉ và ngày nghỉ hiện tại quy định. Đây là đường DUY
     * NHẤT sinh và gỡ slot; gọi lặp lại không thay đổi gì thêm (idempotent). Phải gọi trong giao dịch đã khóa bác
     * sĩ ({@link #lockCurrentDoctor()}).
     *
     * <ol>
     * <li>Lưới: các ô {@code [s + k·m, s + (k+1)·m)} nằm trọn trong ca; dừng khi cộng giờ tràn qua nửa đêm.</li>
     * <li>Bỏ ô giao với giờ nghỉ. Nếu bác sĩ nghỉ cả ngày thì không còn ô nào.</li>
     * <li>Slot đang có mà không còn trong lưới: đang có lịch hẹn giữ chỗ → 409 (hoàn tác tất cả); còn lại thì gỡ
     * (xóa hẳn nếu chưa từng có lịch hẹn, nếu từng có thì giữ ở trạng thái {@code CANCELLED}).</li>
     * <li>Ô trong lưới chưa có slot: tạo slot trống, trừ ô đã qua giờ bắt đầu (hôm nay) hoặc ca của ngày đã qua.
     * Slot đã có thì giữ nguyên, kể cả khi giờ của nó vừa trôi qua.</li>
     * </ol>
     *
     * @return số slot đang hoạt động của ca sau khi dựng lại
     */
    @Transactional
    public int rebuildSlots(DoctorSchedule schedule) {
        return rebuildSlots(schedule, breakRepository.findByDoctorScheduleId(schedule.getId()), slotsOf(schedule));
    }

    /**
     * Dựng lại slot cho mọi ca của bác sĩ trong một ngày (sau khi thêm hoặc xóa ngày nghỉ).
     *
     * @return tổng số slot đang hoạt động của ngày đó
     */
    @Transactional
    public int rebuildDay(Long doctorId, LocalDate date) {
        int total = 0;
        for (DoctorSchedule schedule : scheduleRepository.findByDoctorIdAndWorkDate(doctorId, date)) {
            total += rebuildSlots(schedule);
        }
        return total;
    }

    /**
     * @param existing các slot của ca trước thay đổi (khi sửa giờ ca: theo giờ cũ), gồm cả slot đã gỡ
     */
    private int rebuildSlots(DoctorSchedule schedule, List<ScheduleBreak> breaks, List<AppointmentSlot> existing) {
        Integer minutes = slotMinutesOf(schedule, existing);
        if (minutes != null && schedule.getSlotMinutes() == null) {
            schedule.setSlotMinutes(minutes); // ca tạo trước khi có cột: ghi lại để lần sau không phải suy ra
            scheduleRepository.save(schedule);
        }
        List<AppointmentSlot> live = liveSlots(existing);
        boolean dayOff = dayOffRepository.existsByDoctorIdAndOffDate(schedule.getDoctor().getId(),
                schedule.getWorkDate());
        if (minutes == null && !dayOff) {
            return live.size(); // ca cũ không còn slot nào: không biết độ dài slot nên không có gì để dựng
        }
        List<Cell> grid = dayOff ? List.of()
                : grid(schedule.getStartTime(), schedule.getEndTime(), minutes, breaks);
        return syncSlots(schedule, live, grid);
    }

    /** Gỡ slot thừa, thêm slot thiếu so với {@code wanted}. */
    private int syncSlots(DoctorSchedule schedule, List<AppointmentSlot> live, List<Cell> wanted) {
        Set<Cell> wantedCells = new LinkedHashSet<>(wanted);
        List<AppointmentSlot> surplus = live.stream().filter(slot -> !wantedCells.contains(Cell.of(slot))).toList();
        if (!surplus.isEmpty()) {
            if (!activeAppointments(surplus).isEmpty()) {
                throw new ConflictException("Thay đổi này chạm vào khung giờ đã có lịch hẹn. "
                        + "Hãy xử lý các lịch hẹn đó trước!");
            }
            slotRetirement.retire(surplus);
        }

        Set<Cell> present = new HashSet<>();
        live.stream().map(Cell::of).filter(wantedCells::contains).forEach(present::add);
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        List<AppointmentSlot> created = new ArrayList<>();
        if (!schedule.getWorkDate().isBefore(today)) {
            for (Cell cell : wantedCells) {
                boolean alreadyStarted = schedule.getWorkDate().equals(today) && !cell.start().isAfter(now);
                if (!present.contains(cell) && !alreadyStarted) {
                    created.add(AppointmentSlot.builder()
                            .doctor(schedule.getDoctor())
                            .slotDate(schedule.getWorkDate())
                            .startTime(cell.start())
                            .endTime(cell.end())
                            .isAvailable(true)
                            .status(STATUS_AVAILABLE)
                            .build());
                }
            }
        }
        slotRepository.saveAll(created);
        flushOrConflict();
        return live.size() - surplus.size() + created.size();
    }

    private static List<Cell> grid(LocalTime start, LocalTime end, int minutes, Collection<ScheduleBreak> breaks) {
        List<Cell> cells = new ArrayList<>();
        LocalTime cursor = start;
        while (true) {
            LocalTime cellStart = cursor;
            LocalTime cellEnd = cursor.plusMinutes(minutes);
            if (!cellEnd.isAfter(cellStart) || cellEnd.isAfter(end)) {
                break; // hết ca, hoặc cộng giờ bị tràn qua nửa đêm
            }
            boolean onBreak = breaks.stream()
                    .anyMatch(b -> overlaps(cellStart, cellEnd, b.getStartTime(), b.getEndTime()));
            if (!onBreak) {
                cells.add(new Cell(cellStart, cellEnd));
            }
            cursor = cellEnd;
        }
        return cells;
    }

    /**
     * Việc gỡ slot đi qua entity có {@code @Version}: nếu một bệnh nhân vừa đặt đúng slot đang bị gỡ thì lệnh ghi
     * thất bại ở đây (sai phiên bản, hoặc khóa ngoại {@code appointments.slot_id}) và cả thay đổi được hoàn tác.
     */
    private void flushOrConflict() {
        try {
            slotRepository.flush();
        } catch (ConcurrencyFailureException | DataIntegrityViolationException e) {
            throw new ConflictException("Có người vừa đặt khung giờ này, vui lòng tải lại!");
        }
    }

    // ================= Quy tắc nghiệp vụ =================

    /**
     * Khóa dòng của bác sĩ đang đăng nhập cho tới hết giao dịch, để mọi thay đổi lịch làm việc của bác sĩ đó chạy
     * lần lượt. Gọi ở ĐẦU giao dịch, trước khi đọc ca hay slot. Dùng chung cho module ngày nghỉ.
     */
    @Transactional
    public Doctor lockCurrentDoctor() {
        return doctorRepository.findByIdForUpdate(currentDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ bác sĩ!"));
    }

    private Long currentDoctorId() {
        currentUserService.requireRole("DOCTOR");
        return currentActorService.requireCurrentDoctorId();
    }

    private DoctorSchedule requireOwnedSchedule(Long scheduleId, Long doctorId) {
        DoctorSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ca làm việc!"));
        if (!schedule.getDoctor().getId().equals(doctorId)) {
            throw new ForbiddenException("Ca làm việc này không thuộc về bạn!");
        }
        return schedule;
    }

    private void validateShift(LocalTime start, LocalTime end, int slotMinutes) {
        if (slotMinutes < SLOT_MINUTES_STEP || slotMinutes > MAX_SLOT_MINUTES
                || slotMinutes % SLOT_MINUTES_STEP != 0) {
            throw new FieldValidationException("slotMinutes", "Số phút mỗi slot phải từ " + SLOT_MINUTES_STEP
                    + " đến " + MAX_SLOT_MINUTES + " và chia hết cho " + SLOT_MINUTES_STEP);
        }
        if (!start.isBefore(end)) {
            throw new FieldValidationException("endTime", "Giờ kết thúc phải sau giờ bắt đầu");
        }
        long shiftMinutes = Duration.between(start, end).toMinutes();
        if (shiftMinutes > MAX_SHIFT_HOURS * 60L) {
            throw new FieldValidationException("endTime", "Một ca làm việc dài tối đa " + MAX_SHIFT_HOURS + " giờ");
        }
        if (shiftMinutes < slotMinutes) {
            throw new FieldValidationException("slotMinutes", "Ca làm việc ngắn hơn một slot");
        }
        if (shiftMinutes / slotMinutes > MAX_SLOTS_PER_SHIFT) {
            throw new FieldValidationException("slotMinutes", "Một ca chỉ được chia tối đa " + MAX_SLOTS_PER_SHIFT
                    + " slot. Hãy tăng số phút mỗi slot hoặc rút ngắn ca");
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
    private void assertNoOverlap(List<DoctorSchedule> sameDay, LocalTime start, LocalTime end, Long ignoreScheduleId) {
        Optional<DoctorSchedule> clash = sameDay.stream()
                .filter(other -> !other.getId().equals(ignoreScheduleId))
                .filter(other -> overlaps(start, end, other.getStartTime(), other.getEndTime()))
                .findFirst();
        if (clash.isPresent()) {
            throw new UnprocessableEntityException("Ca làm việc chồng giờ với ca "
                    + clash.get().getStartTime() + "–" + clash.get().getEndTime() + " đã có trong ngày này!");
        }
    }

    /** Có slot nào đang được lịch hẹn giữ chỗ (BOOKED/IN_PROGRESS/COMPLETED) thì không được thay đổi → 409. */
    private void assertNoActiveAppointments(Collection<AppointmentSlot> slots, String action) {
        if (!activeAppointments(slots).isEmpty()) {
            throw new ConflictException("Không thể " + action
                    + " vì đã có lịch hẹn được đặt trong khoảng giờ này. Hãy xử lý các lịch hẹn đó trước!");
        }
    }

    /** slotId → appointmentId của lịch hẹn đang giữ chỗ, tra một lần cho cả lô. */
    private Map<Long, Long> activeAppointments(Collection<AppointmentSlot> slots) {
        Map<Long, Long> appointmentBySlot = new HashMap<>();
        if (slots.isEmpty()) {
            return appointmentBySlot;
        }
        List<Long> ids = slots.stream().map(AppointmentSlot::getId).toList();
        for (Object[] row : appointmentRepository.findActiveBySlotIds(ids, AppointmentStatus.CANCELLED)) {
            appointmentBySlot.put((Long) row[0], (Long) row[1]);
        }
        return appointmentBySlot;
    }

    private void audit(String action, DoctorSchedule schedule, int slots) {
        auditLogService.recordInCurrentTransaction(AuditEvent.of(action, AuditActions.ENTITY_DOCTOR_SCHEDULES,
                schedule.getId()).with("scheduleId", schedule.getId()).with("slots", slots));
    }

    // ================= Dựng DTO =================

    private DoctorScheduleDTO toDTO(DoctorSchedule schedule) {
        List<ScheduleBreak> breaks = breakRepository.findByDoctorScheduleId(schedule.getId());
        return toDTO(schedule, breaks, slotsOf(schedule));
    }

    private DoctorScheduleDTO toDTO(DoctorSchedule schedule, List<ScheduleBreak> breaks, List<AppointmentSlot> slots) {
        List<AppointmentSlot> live = liveSlots(slots);
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
                slotMinutesOf(schedule, slots),
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

    private static List<AppointmentSlot> liveSlots(Collection<AppointmentSlot> slots) {
        return slots.stream().filter(slot -> !isRetired(slot)).toList();
    }

    /**
     * Số phút mỗi slot của ca: giá trị đã lưu; ca tạo trước khi có cột thì lấy độ dài slot ngắn nhất (ưu tiên slot
     * đang hoạt động, rồi đến slot đã gỡ); không có slot nào → {@code null}.
     */
    private static Integer slotMinutesOf(DoctorSchedule schedule, Collection<AppointmentSlot> slots) {
        if (schedule.getSlotMinutes() != null) {
            return schedule.getSlotMinutes();
        }
        return slots.stream()
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
