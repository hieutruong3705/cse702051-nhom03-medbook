package com.phenikaa.cse702051.medbook.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.notification.NotificationDTO;
import com.phenikaa.cse702051.medbook.dto.notification.NotificationMessage;
import com.phenikaa.cse702051.medbook.dto.notification.UnreadCountDTO;
import com.phenikaa.cse702051.medbook.event.AppointmentBookedEvent;
import com.phenikaa.cse702051.medbook.event.AppointmentCancelledEvent;
import com.phenikaa.cse702051.medbook.event.AppointmentRescheduledEvent;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.Notification;
import com.phenikaa.cse702051.medbook.model.Patient;
import com.phenikaa.cse702051.medbook.repository.AppointmentReminderRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.NotificationRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.repository.UserRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.util.PageRequests;

/**
 * Thông báo trong ứng dụng (YCCN-24).
 *
 * <ul>
 * <li><b>Đọc và đánh dấu</b>: mỗi người chỉ thấy và chỉ đánh dấu được thông báo của chính mình (danh tính từ JWT).
 * Thông báo của người khác trả 404 để không lộ sự tồn tại.</li>
 * <li><b>Tạo từ sự kiện lịch hẹn</b>: các phương thức {@code onXxx} chạy trong giao dịch MỚI ({@code REQUIRES_NEW})
 * và chỉ được gọi sau khi giao dịch đặt, đổi, hủy lịch đã commit (xem {@link AppointmentNotificationListener}).</li>
 * <li><b>Nhắc lịch</b>: {@link #remind} giành quyền nhắc bằng một lệnh cập nhật có điều kiện rồi mới tạo thông báo,
 * trong cùng một giao dịch, nên mỗi mốc chỉ nhắc đúng một lần.</li>
 * <li>Nội dung chỉ gồm tên và thời gian khám; không bao giờ chứa ghi chú, lý do hủy hay thông tin lâm sàng.</li>
 * </ul>
 */
@Service
public class NotificationService {

    public static final String APPOINTMENT_BOOKED = "APPOINTMENT_BOOKED";
    public static final String APPOINTMENT_RESCHEDULED = "APPOINTMENT_RESCHEDULED";
    public static final String APPOINTMENT_CANCELLED = "APPOINTMENT_CANCELLED";
    public static final String REMINDER_24H = "REMINDER_24H";
    public static final String REMINDER_2H = "REMINDER_2H";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt")
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final NotificationRepository notificationRepository;
    private final AppointmentReminderRepository reminderRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public NotificationService(
            NotificationRepository notificationRepository,
            AppointmentReminderRepository reminderRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService) {
        this.notificationRepository = notificationRepository;
        this.reminderRepository = reminderRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    // ================= Đọc và đánh dấu (người đang đăng nhập) =================

    @Transactional(readOnly = true)
    public PageResponse<NotificationDTO> listMine(boolean unreadOnly, int page, int size) {
        Long userId = currentUserService.requireRole("PATIENT", "DOCTOR").userId();
        PageRequest pageable = PageRequests.of(page, size, NEWEST_FIRST);
        Page<Notification> result = unreadOnly
                ? notificationRepository.findByUserIdAndReadAtIsNull(userId, pageable)
                : notificationRepository.findByUserId(userId, pageable);
        return PageResponse.from(result, NotificationDTO::from);
    }

    @Transactional(readOnly = true)
    public UnreadCountDTO countUnread() {
        Long userId = currentUserService.requireRole("PATIENT", "DOCTOR").userId();
        return new UnreadCountDTO(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    /** Đánh dấu đã đọc. Thông báo không tồn tại hoặc của người khác → 404 và không thay đổi gì. */
    @Transactional
    public void markRead(Long notificationId) {
        Long userId = currentUserService.requireRole("PATIENT", "DOCTOR").userId();
        if (!notificationRepository.existsByIdAndUserId(notificationId, userId)) {
            throw new ResourceNotFoundException("Không tìm thấy thông báo!");
        }
        notificationRepository.markRead(notificationId, userId, LocalDateTime.now());
    }

    @Transactional
    public void markAllRead() {
        Long userId = currentUserService.requireRole("PATIENT", "DOCTOR").userId();
        notificationRepository.markAllRead(userId, LocalDateTime.now());
    }

    // ================= Tạo từ sự kiện lịch hẹn (gọi sau khi giao dịch gốc đã commit) =================

    /** Đặt lịch thành công: một thông báo cho bệnh nhân và một cho bác sĩ. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationMessage> onBooked(AppointmentBookedEvent event) {
        Patient patient = patientRepository.findById(event.patientId()).orElse(null);
        Doctor doctor = doctorRepository.findById(event.doctorId()).orElse(null);
        String when = when(event.date(), event.startTime());
        List<NotificationMessage> created = new ArrayList<>();
        notifyPatient(created, patient, APPOINTMENT_BOOKED, "Đặt lịch khám thành công",
                "Bạn đã đặt lịch khám với " + doctorLabel(doctor) + " " + when + ".", event.appointmentId());
        notifyDoctor(created, doctor, APPOINTMENT_BOOKED, "Có lịch khám mới",
                patientLabel(patient) + " đã đặt lịch khám " + when + ".", event.appointmentId());
        return created;
    }

    /** Đổi lịch thành công: bệnh nhân và (các) bác sĩ liên quan đều nhận thông báo nêu giờ mới. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationMessage> onRescheduled(AppointmentRescheduledEvent event) {
        Patient patient = patientRepository.findById(event.patientId()).orElse(null);
        Doctor newDoctor = doctorRepository.findById(event.newDoctorId()).orElse(null);
        String oldWhen = when(event.oldDate(), event.oldStartTime());
        String newWhen = when(event.newDate(), event.newStartTime());
        List<NotificationMessage> created = new ArrayList<>();
        notifyPatient(created, patient, APPOINTMENT_RESCHEDULED, "Đổi lịch khám thành công",
                "Lịch khám của bạn với " + doctorLabel(newDoctor) + " đã được đổi sang " + newWhen
                        + " (trước đó " + oldWhen + ").", event.appointmentId());
        if (event.newDoctorId().equals(event.oldDoctorId())) {
            notifyDoctor(created, newDoctor, APPOINTMENT_RESCHEDULED, "Lịch khám được đổi giờ",
                    patientLabel(patient) + " đã đổi lịch khám từ " + oldWhen + " sang " + newWhen + ".",
                    event.appointmentId());
        } else {
            Doctor oldDoctor = doctorRepository.findById(event.oldDoctorId()).orElse(null);
            notifyDoctor(created, oldDoctor, APPOINTMENT_RESCHEDULED, "Lịch khám được chuyển đi",
                    patientLabel(patient) + " đã chuyển lịch khám " + oldWhen + " sang bác sĩ khác.",
                    event.appointmentId());
            notifyDoctor(created, newDoctor, APPOINTMENT_RESCHEDULED, "Có lịch khám mới",
                    patientLabel(patient) + " đã chuyển lịch khám sang bạn, khám " + newWhen + ".",
                    event.appointmentId());
        }
        return created;
    }

    /** Bệnh nhân hủy lịch: bác sĩ nhận thông báo. Lý do hủy không được đưa vào nội dung. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationMessage> onCancelled(AppointmentCancelledEvent event) {
        Patient patient = patientRepository.findById(event.patientId()).orElse(null);
        Doctor doctor = doctorRepository.findById(event.doctorId()).orElse(null);
        List<NotificationMessage> created = new ArrayList<>();
        notifyDoctor(created, doctor, APPOINTMENT_CANCELLED, "Lịch khám đã bị hủy",
                patientLabel(patient) + " đã hủy lịch khám " + when(event.date(), event.startTime()) + ".",
                event.appointmentId());
        return created;
    }

    // ================= Nhắc lịch =================

    /**
     * Nhắc bệnh nhân về một lịch khám ở mốc 24 giờ hoặc 2 giờ. Chỉ khi giành được quyền nhắc (lịch còn BOOKED và
     * mốc này chưa được nhắc) mới tạo thông báo; cả hai nằm trong một giao dịch nên lỗi khi tạo thông báo sẽ
     * hoàn lại quyền nhắc để lần chạy sau thử lại.
     *
     * @param type {@link #REMINDER_24H} hoặc {@link #REMINDER_2H}
     * @return thông báo đã tạo; rỗng nếu lịch đã được nhắc, đã đổi trạng thái, hoặc bệnh nhân không có tài khoản
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<NotificationMessage> remind(Long appointmentId, String type, LocalDateTime now) {
        int claimed = REMINDER_2H.equals(type)
                ? reminderRepository.claim2h(appointmentId, now, AppointmentStatus.BOOKED)
                : reminderRepository.claim24h(appointmentId, now, AppointmentStatus.BOOKED);
        if (claimed != 1) {
            return Optional.empty();
        }
        Appointment appointment = reminderRepository.findWithSlotAndDoctor(appointmentId).orElse(null);
        if (appointment == null) {
            return Optional.empty();
        }
        Patient patient = patientRepository.findById(appointment.getPatientId()).orElse(null);
        String when = when(appointment.getSlot().getSlotDate(), appointment.getSlot().getStartTime());
        String title = REMINDER_2H.equals(type) ? "Sắp đến giờ khám" : "Nhắc lịch khám";
        String message = REMINDER_2H.equals(type)
                ? "Bạn có lịch khám với " + doctorLabel(appointment.getDoctor()) + " trong vòng 2 giờ tới, " + when
                        + ". Vui lòng có mặt trước giờ hẹn."
                : "Bạn có lịch khám với " + doctorLabel(appointment.getDoctor()) + " " + when + ".";
        List<NotificationMessage> created = new ArrayList<>();
        notifyPatient(created, patient, type, title, message, appointmentId);
        return created.stream().findFirst();
    }

    // ================= Chi tiết =================

    private void notifyPatient(List<NotificationMessage> created, Patient patient, String type, String title,
            String message, Long appointmentId) {
        // Bệnh nhân do phòng khám tạo có thể chưa có tài khoản: không có ai để nhận thông báo trong ứng dụng.
        if (patient != null && patient.getUser() != null) {
            created.add(save(patient.getUser().getId(), type, title, message, appointmentId));
        }
    }

    private void notifyDoctor(List<NotificationMessage> created, Doctor doctor, String type, String title,
            String message, Long appointmentId) {
        if (doctor != null && doctor.getUserId() != null) {
            created.add(save(doctor.getUserId(), type, title, message, appointmentId));
        }
    }

    private NotificationMessage save(Long userId, String type, String title, String message, Long appointmentId) {
        Notification notification = notificationRepository.save(Notification.builder()
                .user(userRepository.getReferenceById(userId))
                .type(type)
                .title(title)
                .message(message)
                .appointmentId(appointmentId)
                .createdAt(LocalDateTime.now())
                .build());
        return new NotificationMessage(notification.getId(), userId, type, title, message, appointmentId);
    }

    private static String when(LocalDate date, LocalTime time) {
        return "lúc " + TIME.format(time) + " ngày " + DATE.format(date);
    }

    private static String doctorLabel(Doctor doctor) {
        return doctor == null ? "bác sĩ" : "BS. " + doctor.getFullName();
    }

    private static String patientLabel(Patient patient) {
        return patient == null ? "Bệnh nhân" : "Bệnh nhân " + patient.getFullName();
    }
}
