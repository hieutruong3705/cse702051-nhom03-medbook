package com.phenikaa.cse702051.medbook.auth;

import static org.junit.jupiter.api.Assertions.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import jakarta.validation.Validation;
import com.phenikaa.cse702051.medbook.dto.*;

class BcryptLengthTest {
    static Stream<String> allowed() {
        return Stream.of("a1" + "x".repeat(70), "a1" + "é".repeat(35));
    }
    static Stream<String> rejected() {
        return Stream.of("a1" + "x".repeat(71), "a1x" + "é".repeat(35));
    }
    @ParameterizedTest @MethodSource("allowed")
    void acceptsExactly72Utf8Bytes(String value) { check(value, true); }
    @ParameterizedTest @MethodSource("rejected")
    void rejects73Utf8BytesAcrossAllPasswordEntryPoints(String value) { check(value, false); }
    private void check(String value, boolean accepted) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertEquals(accepted, validator.validateValue(RegisterRequest.class, "password", value).isEmpty());
            assertEquals(accepted, validator.validateValue(ChangePasswordRequest.class, "newPassword", value).isEmpty());
            assertEquals(accepted, validator.validateValue(ResetPasswordRequest.class, "newPassword", value).isEmpty());
        }
    }
}
