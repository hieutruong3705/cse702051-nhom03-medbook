package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.Role;

@Repository
public class RoleRepository {

    private final Map<Long, Role> rolesById = new ConcurrentHashMap<>();
    private final Map<String, Long> idsByCode = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(10);

    public RoleRepository() {
        LocalDateTime now = LocalDateTime.now();
        saveDirect(Role.builder().id(1L).code("ADMIN").name("Quản trị viên").description("Toàn quyền hệ thống").createdAt(now).build());
        saveDirect(Role.builder().id(2L).code("DOCTOR").name("Bác sĩ").description("Khám bệnh và kê đơn").createdAt(now).build());
        saveDirect(Role.builder().id(3L).code("PATIENT").name("Bệnh nhân").description("Người bệnh đăng ký khám").createdAt(now).build());
        saveDirect(Role.builder().id(4L).code("RECEPTIONIST").name("Lễ tân").description("Điều phối tiếp đón").createdAt(now).build());
    }

    private void saveDirect(Role role) {
        rolesById.put(role.getId(), role);
        idsByCode.put(role.getCode().toUpperCase(), role.getId());
    }

    public Role save(Role role) {
        if (role.getId() == null) {
            role.setId(idGenerator.incrementAndGet());
        }
        saveDirect(role);
        return role;
    }

    public Optional<Role> findById(Long id) {
        return Optional.ofNullable(rolesById.get(id));
    }

    public Optional<Role> findByCode(String code) {
        if (code == null) return Optional.empty();
        Long id = idsByCode.get(code.toUpperCase());
        return id != null ? Optional.ofNullable(rolesById.get(id)) : Optional.empty();
    }
}
