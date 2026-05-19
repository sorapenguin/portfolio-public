package com.ec.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    // 256bit以上必要（HMAC-SHA256最小要件）
    private static final String SECRET = "ec-site-test-secret-key-min-256bits-long!!";
    private static final long EXPIRATION_MS = 3_600_000L; // 1時間

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", EXPIRATION_MS);
    }

    @Test
    void generate_validToken_isValid() {
        String token = jwtUtil.generate("user1", "USER");
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void generate_extractsCorrectUsername() {
        String token = jwtUtil.generate("alice", "USER");
        assertThat(jwtUtil.username(token)).isEqualTo("alice");
    }

    @Test
    void generate_extractsCorrectRole() {
        String token = jwtUtil.generate("admin1", "ADMIN");
        assertThat(jwtUtil.role(token)).isEqualTo("ADMIN");
    }

    @Test
    void generate_userAndAdminRolesDistinct() {
        String userToken = jwtUtil.generate("user1", "USER");
        String adminToken = jwtUtil.generate("admin1", "ADMIN");

        assertThat(jwtUtil.role(userToken)).isEqualTo("USER");
        assertThat(jwtUtil.role(adminToken)).isEqualTo("ADMIN");
    }

    @Test
    void expiredToken_isNotValid() {
        JwtUtil expiredUtil = new JwtUtil();
        ReflectionTestUtils.setField(expiredUtil, "secret", SECRET);
        ReflectionTestUtils.setField(expiredUtil, "expiration", -1L); // 過去の期限

        String token = expiredUtil.generate("user1", "USER");

        assertThat(jwtUtil.isValid(token)).isFalse();
    }

    @Test
    void tamperedToken_isNotValid() {
        String token = jwtUtil.generate("user1", "USER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }

    @Test
    void emptyString_isNotValid() {
        assertThat(jwtUtil.isValid("")).isFalse();
    }

    @Test
    void randomString_isNotValid() {
        assertThat(jwtUtil.isValid("not.a.jwt.token")).isFalse();
    }
}
