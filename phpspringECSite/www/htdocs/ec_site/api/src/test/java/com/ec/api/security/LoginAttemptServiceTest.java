package com.ec.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
    }

    @Test
    void isLocked_noAttempts_returnsFalse() {
        assertThat(service.isLocked("user1")).isFalse();
    }

    @Test
    void isLocked_belowMaxAttempts_returnsFalse() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS - 1; i++) {
            service.recordFailure("user1");
        }
        assertThat(service.isLocked("user1")).isFalse();
    }

    @Test
    void isLocked_atMaxAttempts_returnsTrue() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.recordFailure("user1");
        }
        assertThat(service.isLocked("user1")).isTrue();
    }

    @Test
    void isLocked_exceedsMaxAttempts_remainsLocked() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS + 3; i++) {
            service.recordFailure("user1");
        }
        assertThat(service.isLocked("user1")).isTrue();
    }

    @Test
    void resetAttempts_clearsLock() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.recordFailure("user1");
        }
        assertThat(service.isLocked("user1")).isTrue();

        service.resetAttempts("user1");

        assertThat(service.isLocked("user1")).isFalse();
    }

    @Test
    void resetAttempts_nonExistentUser_noError() {
        assertThatNoException().isThrownBy(() -> service.resetAttempts("nobody"));
    }

    @Test
    void lockout_isPerUser_doesNotAffectOtherUsers() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.recordFailure("user1");
        }
        assertThat(service.isLocked("user1")).isTrue();
        assertThat(service.isLocked("user2")).isFalse();
    }

    @Test
    void recordFailure_afterReset_startsCountFromZero() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.recordFailure("user1");
        }
        service.resetAttempts("user1");

        // リセット後はMAX-1回失敗しても未ロック
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS - 1; i++) {
            service.recordFailure("user1");
        }
        assertThat(service.isLocked("user1")).isFalse();
    }
}
