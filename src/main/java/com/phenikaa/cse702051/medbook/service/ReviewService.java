package com.phenikaa.cse702051.medbook.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phenikaa.cse702051.medbook.config.CacheConfig;
import com.phenikaa.cse702051.medbook.dto.PageResponse;
import com.phenikaa.cse702051.medbook.dto.review.HideReviewRequest;
import com.phenikaa.cse702051.medbook.dto.review.RatingSummaryDTO;
import com.phenikaa.cse702051.medbook.dto.review.ReviewAdminDTO;
import com.phenikaa.cse702051.medbook.dto.review.ReviewDTO;
import com.phenikaa.cse702051.medbook.dto.review.ReviewPublicDTO;
import com.phenikaa.cse702051.medbook.dto.review.ReviewRequest;
import com.phenikaa.cse702051.medbook.event.DoctorReviewedEvent;
import com.phenikaa.cse702051.medbook.exception.BadRequestException;
import com.phenikaa.cse702051.medbook.exception.ConflictException;
import com.phenikaa.cse702051.medbook.exception.ForbiddenException;
import com.phenikaa.cse702051.medbook.exception.ResourceNotFoundException;
import com.phenikaa.cse702051.medbook.exception.UnprocessableEntityException;
import com.phenikaa.cse702051.medbook.model.Appointment;
import com.phenikaa.cse702051.medbook.model.AppointmentStatus;
import com.phenikaa.cse702051.medbook.model.Doctor;
import com.phenikaa.cse702051.medbook.model.DoctorReview;
import com.phenikaa.cse702051.medbook.repository.AppointmentRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorRepository;
import com.phenikaa.cse702051.medbook.repository.DoctorReviewRepository;
import com.phenikaa.cse702051.medbook.repository.PatientRepository;
import com.phenikaa.cse702051.medbook.security.CurrentUserService;
import com.phenikaa.cse702051.medbook.util.PageRequests;

import jakarta.persistence.criteria.Predicate;

/**
 * Đánh giá lần khám (YCCN-38 đến 41, 44).
 *
 * <ul>
 * <li><b>Bệnh nhân</b>: chỉ đánh giá lịch hẹn của chính mình (danh tính từ JWT), chỉ khi lịch đã COMPLETED, mỗi
 * lịch một lần. Bác sĩ được đánh giá lấy từ lịch hẹn, không do trình duyệt gửi lên. Sửa, xóa được đánh giá của mình
 * khi chưa bị ẩn.</li>
 * <li><b>Công khai</b>: chỉ đánh giá VISIBLE của bác sĩ đang hoạt động, tên người đánh giá đã rút gọn.</li>
 * <li><b>Quản trị viên</b>: ẩn kèm lý do và hiện lại; mọi thay đổi đều ghi nhật ký. Đánh giá bị ẩn không tính
 * vào điểm trung bình.</li>
 * </ul>
 */
@Service
public class ReviewService {

    private static final Set<String> PUBLIC_SORTS = Set.of("createdAt", "rating");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt")
            .and(Sort.by(Sort.Direction.DESC, "id"));
    private static final Set<String> STATUSES = Set.of(DoctorReview.VISIBLE, DoctorReview.HIDDEN);

    private final DoctorReviewRepository reviewRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final CurrentUserService currentUserService;
    private final CurrentActorService currentActorService;
    private final AuditLogService auditLogService;
    private final PublicCatalogCache publicCatalogCache;
    private final ApplicationEventPublisher eventPublisher;

    public ReviewService(
            DoctorReviewRepository reviewRepository,
            AppointmentRepository appointmentRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            CurrentUserService currentUserService,
            CurrentActorService currentActorService,
            AuditLogService auditLogService,
            PublicCatalogCache publicCatalogCache,
            ApplicationEventPublisher eventPublisher) {
        this.reviewRepository = reviewRepository;
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.currentUserService = currentUserService;
        this.currentActorService = currentActorService;
        this.auditLogService = auditLogService;
        this.publicCatalogCache = publicCatalogCache;
        this.eventPublisher = eventPublisher;
    }

    // ================= Bệnh nhân =================

    @Transactional
    public ReviewDTO create(Long appointmentId, ReviewRequest request) {
        currentUserService.requireRole("PATIENT");
        Long patientId = currentActorService.requireCurrentPatientId();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn!"));
        if (!patientId.equals(appointment.getPatientId())) {
            auditLogService.recordAccessDenied(AuditActions.ENTITY_APPOINTMENTS, appointmentId,
                    "Đánh giá lịch hẹn của bệnh nhân khác");
            throw new ForbiddenException("Bạn chỉ đánh giá được lịch khám của chính mình!");
        }
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new UnprocessableEntityException("Chỉ đánh giá được lần khám đã hoàn thành!");
        }
        if (reviewRepository.existsByAppointmentId(appointmentId)) {
            throw new ConflictException("Lần khám này đã được đánh giá!");
        }
        Doctor doctor = appointment.getDoctor();
        DoctorReview review;
        try {
            review = reviewRepository.saveAndFlush(DoctorReview.builder()
                    .appointmentId(appointmentId)
                    .patient(patientRepository.getReferenceById(patientId))
                    .doctor(doctor)
                    .rating(request.rating())
                    .comment(cleanComment(request.comment()))
                    .status(DoctorReview.VISIBLE)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(appointment_id): hai yêu cầu đánh giá cùng một lịch gửi đồng thời
            throw new ConflictException("Lần khám này đã được đánh giá!");
        }
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.REVIEW_CREATE,
                AuditActions.ENTITY_DOCTOR_REVIEWS, review.getId())
                .with("appointmentId", appointmentId).with("doctorId", doctor.getId())
                .with("rating", review.getRating()));
        publicCatalogCache.evictAfterCommit(CacheConfig.PUBLIC_CLINIC);
        eventPublisher.publishEvent(new DoctorReviewedEvent(review.getId(), appointmentId, doctor.getId(),
                review.getRating()));
        return ReviewDTO.from(reload(review.getId()));
    }

    @Transactional
    public ReviewDTO update(Long reviewId, ReviewRequest request) {
        DoctorReview review = requireOwnReview(reviewId);
        if (!review.isVisible()) {
            throw new ConflictException("Đánh giá đã bị quản trị viên ẩn, không sửa được!");
        }
        review.setRating(request.rating());
        review.setComment(cleanComment(request.comment()));
        reviewRepository.saveAndFlush(review);
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.REVIEW_UPDATE,
                AuditActions.ENTITY_DOCTOR_REVIEWS, reviewId).with("rating", review.getRating()));
        publicCatalogCache.evictAfterCommit(CacheConfig.PUBLIC_CLINIC);
        return ReviewDTO.from(review);
    }

    @Transactional
    public void delete(Long reviewId) {
        DoctorReview review = requireOwnReview(reviewId);
        if (!review.isVisible()) {
            throw new ConflictException("Đánh giá đã bị quản trị viên ẩn, không xóa được!");
        }
        reviewRepository.delete(review);
        reviewRepository.flush();
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.REVIEW_DELETE,
                AuditActions.ENTITY_DOCTOR_REVIEWS, reviewId)
                .with("appointmentId", review.getAppointmentId()).with("doctorId", review.getDoctor().getId()));
        publicCatalogCache.evictAfterCommit(CacheConfig.PUBLIC_CLINIC);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewDTO> listMine(int page, int size) {
        currentUserService.requireRole("PATIENT");
        Long patientId = currentActorService.requireCurrentPatientId();
        Specification<DoctorReview> mine = (root, query, cb) -> cb.equal(root.get("patient").get("id"), patientId);
        return PageResponse.from(reviewRepository.findAll(mine, PageRequests.of(page, size, NEWEST_FIRST)),
                ReviewDTO::from);
    }

    /** Đánh giá của bệnh nhân cho một lịch hẹn của mình; chưa đánh giá thì 404. */
    @Transactional(readOnly = true)
    public ReviewDTO getMineForAppointment(Long appointmentId) {
        currentUserService.requireRole("PATIENT");
        Long patientId = currentActorService.requireCurrentPatientId();
        return reviewRepository.findByAppointmentId(appointmentId)
                .filter(review -> patientId.equals(review.getPatient().getId()))
                .map(ReviewDTO::from)
                .orElseThrow(() -> new ResourceNotFoundException("Lần khám này chưa có đánh giá!"));
    }

    // ================= Bác sĩ =================

    /** Bác sĩ xem đánh giá đang hiện về chính mình (tên người đánh giá đã rút gọn như trang công khai). */
    @Transactional(readOnly = true)
    public PageResponse<ReviewPublicDTO> listForCurrentDoctor(int page, int size) {
        currentUserService.requireRole("DOCTOR");
        Long doctorId = currentActorService.requireCurrentDoctorId();
        return PageResponse.from(reviewRepository.findAll(visible(doctorId, null, null),
                PageRequests.of(page, size, NEWEST_FIRST)), ReviewPublicDTO::from);
    }

    @Transactional(readOnly = true)
    public RatingSummaryDTO summaryForCurrentDoctor() {
        currentUserService.requireRole("DOCTOR");
        return summarize(currentActorService.requireCurrentDoctorId());
    }

    // ================= Công khai =================

    /** {@code sort}: {@code createdAt} (mặc định, mới nhất trước) hoặc {@code rating}, có thể kèm {@code ,asc|desc}. */
    @Transactional(readOnly = true)
    public PageResponse<ReviewPublicDTO> listPublic(Long doctorId, Long specialtyId, Integer rating, int page,
            int size, String sort) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BadRequestException("Số sao lọc phải từ 1 đến 5");
        }
        Sort order = PageRequests.parseSort(sort, PUBLIC_SORTS, NEWEST_FIRST);
        Page<DoctorReview> result = reviewRepository.findAll(visible(doctorId, specialtyId, rating),
                PageRequests.of(page, size, order));
        return PageResponse.from(result, ReviewPublicDTO::from);
    }

    /** Đánh giá đang hiện của một bác sĩ đang hoạt động; bác sĩ không tồn tại hoặc ngừng hoạt động → 404. */
    @Transactional(readOnly = true)
    public PageResponse<ReviewPublicDTO> listPublicForDoctor(Long doctorId, Integer rating, int page, int size,
            String sort) {
        requireActiveDoctor(doctorId);
        return listPublic(doctorId, null, rating, page, size, sort);
    }

    /** Tóm tắt điểm của một bác sĩ đang hoạt động; bác sĩ không tồn tại hoặc đã ngừng hoạt động → 404. */
    @Transactional(readOnly = true)
    public RatingSummaryDTO summaryForDoctor(Long doctorId) {
        requireActiveDoctor(doctorId);
        return summarize(doctorId);
    }

    /** Điểm trung bình và số đánh giá của nhiều bác sĩ, gộp trong một truy vấn cho cả trang danh sách. */
    @Transactional(readOnly = true)
    public Map<Long, RatingSummaryDTO> summariesFor(Collection<Long> doctorIds) {
        Map<Long, RatingSummaryDTO> summaries = new HashMap<>();
        if (doctorIds.isEmpty()) {
            return summaries;
        }
        for (Object[] row : reviewRepository.summarizeVisibleByDoctorIds(doctorIds)) {
            Long doctorId = (Long) row[0];
            summaries.put(doctorId, new RatingSummaryDTO(doctorId, round(row[1]), ((Number) row[2]).longValue(),
                    List.of()));
        }
        return summaries;
    }

    // ================= Quản trị viên =================

    @Transactional(readOnly = true)
    public PageResponse<ReviewAdminDTO> listForAdmin(String status, Long doctorId, Integer rating, int page,
            int size) {
        currentUserService.requireRole("ADMIN");
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new BadRequestException("Trạng thái phải là VISIBLE hoặc HIDDEN");
        }
        Specification<DoctorReview> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                all.add(cb.equal(root.get("status"), status));
            }
            if (doctorId != null) {
                all.add(cb.equal(root.get("doctor").get("id"), doctorId));
            }
            if (rating != null) {
                all.add(cb.equal(root.get("rating"), rating));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
        return PageResponse.from(reviewRepository.findAll(spec, PageRequests.of(page, size, NEWEST_FIRST)),
                ReviewAdminDTO::from);
    }

    @Transactional
    public ReviewAdminDTO hide(Long reviewId, HideReviewRequest request) {
        Long adminId = currentUserService.requireRole("ADMIN").userId();
        DoctorReview review = findWithDetails(reviewId);
        if (!review.isVisible()) {
            throw new ConflictException("Đánh giá này đang bị ẩn!");
        }
        review.setStatus(DoctorReview.HIDDEN);
        review.setHiddenReason(request.reason().trim());
        review.setHiddenBy(adminId);
        review.setHiddenAt(LocalDateTime.now());
        reviewRepository.saveAndFlush(review);
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.REVIEW_HIDE,
                AuditActions.ENTITY_DOCTOR_REVIEWS, reviewId).with("reason", review.getHiddenReason()));
        publicCatalogCache.evictAfterCommit(CacheConfig.PUBLIC_CLINIC);
        return ReviewAdminDTO.from(review);
    }

    @Transactional
    public ReviewAdminDTO show(Long reviewId) {
        currentUserService.requireRole("ADMIN");
        DoctorReview review = findWithDetails(reviewId);
        if (review.isVisible()) {
            throw new ConflictException("Đánh giá này đang hiện!");
        }
        review.setStatus(DoctorReview.VISIBLE);
        review.setHiddenReason(null);
        review.setHiddenBy(null);
        review.setHiddenAt(null);
        reviewRepository.saveAndFlush(review);
        auditLogService.recordInCurrentTransaction(AuditEvent.of(AuditActions.REVIEW_SHOW,
                AuditActions.ENTITY_DOCTOR_REVIEWS, reviewId));
        publicCatalogCache.evictAfterCommit(CacheConfig.PUBLIC_CLINIC);
        return ReviewAdminDTO.from(review);
    }

    // ================= Chi tiết =================

    /**
     * Đánh giá của bệnh nhân đang đăng nhập. Đánh giá của người khác trả 404 như không tồn tại (không lộ sự tồn
     * tại) và ghi một dòng ACCESS_DENIED.
     */
    private DoctorReview requireOwnReview(Long reviewId) {
        currentUserService.requireRole("PATIENT");
        Long patientId = currentActorService.requireCurrentPatientId();
        DoctorReview review = findWithDetails(reviewId);
        if (!patientId.equals(review.getPatient().getId())) {
            auditLogService.recordAccessDenied(AuditActions.ENTITY_DOCTOR_REVIEWS, reviewId,
                    "Sửa hoặc xóa đánh giá của bệnh nhân khác");
            throw new ResourceNotFoundException("Không tìm thấy đánh giá!");
        }
        return review;
    }

    private DoctorReview findWithDetails(Long reviewId) {
        return reviewRepository.findWithDetailsById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đánh giá!"));
    }

    private DoctorReview reload(Long reviewId) {
        return findWithDetails(reviewId);
    }

    private void requireActiveDoctor(Long doctorId) {
        doctorRepository.findById(doctorId)
                .filter(doctor -> Boolean.TRUE.equals(doctor.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bác sĩ!"));
    }

    private RatingSummaryDTO summarize(Long doctorId) {
        long[] counts = new long[5];
        long total = 0;
        long sum = 0;
        for (Object[] row : reviewRepository.countVisibleByRating(doctorId)) {
            int stars = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            counts[stars - 1] = count;
            total += count;
            sum += stars * count;
        }
        Double average = total == 0 ? null : round((double) sum / total);
        return new RatingSummaryDTO(doctorId, average, total, Arrays.stream(counts).boxed().toList());
    }

    /** Đánh giá đang hiện của bác sĩ đang hoạt động, lọc tùy chọn theo bác sĩ, chuyên khoa, số sao. */
    private static Specification<DoctorReview> visible(Long doctorId, Long specialtyId, Integer rating) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.equal(root.get("status"), DoctorReview.VISIBLE));
            all.add(cb.isTrue(root.get("doctor").get("isActive")));
            if (doctorId != null) {
                all.add(cb.equal(root.get("doctor").get("id"), doctorId));
            }
            if (specialtyId != null) {
                all.add(cb.equal(root.get("doctor").get("specialty").get("id"), specialtyId));
            }
            if (rating != null) {
                all.add(cb.equal(root.get("rating"), rating));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    static Double round(Object average) {
        if (average == null) {
            return null;
        }
        return BigDecimal.valueOf(((Number) average).doubleValue()).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Nhận xét chỉ là văn bản thuần: bỏ ký tự điều khiển (trừ xuống dòng), gộp quá hai dòng trống, cắt khoảng trắng
     * hai đầu; rỗng thì lưu {@code null}. Không lọc thẻ HTML ở đây vì giao diện luôn hiển thị dạng văn bản (Vue tự
     * mã hóa), nên nội dung người dùng gõ được giữ nguyên văn.
     */
    static String cleanComment(String comment) {
        if (comment == null) {
            return null;
        }
        String cleaned = comment.replace("\r\n", "\n")
                .replaceAll("[\\p{Cntrl}&&[^\n]]", "")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
