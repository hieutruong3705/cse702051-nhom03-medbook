package com.phenikaa.cse702051.medbook.repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

import com.phenikaa.cse702051.medbook.model.User;

@Repository
public class UserRepository {

    private final Map<Long, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, Long> idsByUsername = new ConcurrentHashMap<>();
    private final Map<String, Long> idsByEmail = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(10);

    public UserRepository() {
        initSeedData();
    }

    private void initSeedData() {
        LocalDateTime now = LocalDateTime.now();
        // Mật khẩu băm BCrypt cost 12 đã kiểm chứng khớp 100% với P@ssword123
        String passHash = "$2b$12$0d1UDbouI7XsgxTPca.ay.F7Jz80a9DNFDsLYceCN2Hw0foqVMpBy"; // P@ssword123
        String adminHash = "$2b$12$5eKhYoOnPDBzHptueRZq3u8UqfNrxR77DDPcgKQBSq55HMMLFT0uu"; // P@ssword123
        String docHash = "$2b$12$wOawQxWQJNu8mxUFh6e7JeLZ.9TWExqPMtxDf/AI3aJVJghP0VFh2";   // P@ssword123

        saveDirect(User.builder().id(1L).username("admin.demo").passwordHash(adminHash).fullName("Quản trị viên Demo").email("admin.demo@example.test").phone("0900000001").status("ACTIVE").createdAt(now).updatedAt(now).build());
        saveDirect(User.builder().id(2L).username("doctor.lan").passwordHash(docHash).fullName("BS. Nguyễn Thị Lan").email("doctor.lan@example.test").phone("0900000002").status("ACTIVE").createdAt(now).updatedAt(now).build());
        saveDirect(User.builder().id(3L).username("doctor.huy").passwordHash(docHash).fullName("BS. Trần Quốc Huy").email("doctor.huy@example.test").phone("0900000003").status("ACTIVE").createdAt(now).updatedAt(now).build());
        saveDirect(User.builder().id(4L).username("reception.demo").passwordHash(docHash).fullName("Lễ tân Demo").email("reception@example.test").phone("0900000004").status("ACTIVE").createdAt(now).updatedAt(now).build());
        saveDirect(User.builder().id(5L).username("patient.an").passwordHash(passHash).fullName("Nguyễn Minh An").email("patient.an@example.test").phone("0900000005").status("ACTIVE").createdAt(now).updatedAt(now).build());
        saveDirect(User.builder().id(6L).username("patient.binh").passwordHash(passHash).fullName("Trần Gia Bình").email("patient.binh@example.test").phone("0900000006").status("ACTIVE").createdAt(now).updatedAt(now).build());
        saveDirect(User.builder().id(7L).username("patient.chi").passwordHash(passHash).fullName("Lê Ngọc Chi").email("patient.chi@example.test").phone("0900000007").status("ACTIVE").createdAt(now).updatedAt(now).build());
    }

    private void saveDirect(User user) {
        usersById.put(user.getId(), user);
        idsByUsername.put(user.getUsername().toLowerCase(), user.getId());
        if (user.getEmail() != null) {
            idsByEmail.put(user.getEmail().toLowerCase(), user.getId());
        }
    }

    public User save(User user) {
        if (user.getId() == null) {
            user.setId(idGenerator.incrementAndGet());
        }
        saveDirect(user);
        return user;
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(usersById.get(id));
    }

    public Optional<User> findByUsername(String username) {
        if (username == null) return Optional.empty();
        Long id = idsByUsername.get(username.toLowerCase());
        return id != null ? Optional.ofNullable(usersById.get(id)) : Optional.empty();
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        Long id = idsByEmail.get(email.toLowerCase());
        return id != null ? Optional.ofNullable(usersById.get(id)) : Optional.empty();
    }

    public boolean existsByUsername(String username) {
        return username != null && idsByUsername.containsKey(username.toLowerCase());
    }

    public boolean existsByEmail(String email) {
        return email != null && idsByEmail.containsKey(email.toLowerCase());
    }
}
