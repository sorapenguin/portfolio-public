package com.ec.api.security;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ブルートフォース対策: MAX_ATTEMPTS 回ログイン失敗で LOCK_DURATION_MS ミリ秒ロック。
 * インメモリ管理のためアプリ再起動でリセットされる（ポートフォリオ用途では許容）。
 */
@Service
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    public static final long LOCK_DURATION_MS = 15 * 60 * 1000L; // 15分

    // username -> [failCount, lockUntilMs]
    private final ConcurrentHashMap<String, long[]> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String username) {
        long[] data = attempts.get(username);
        if (data == null || data[0] < MAX_ATTEMPTS) return false;
        if (System.currentTimeMillis() > data[1]) {
            attempts.remove(username);
            return false;
        }
        return true;
    }

    public void recordFailure(String username) {
        long[] data = attempts.compute(username, (k, v) -> {
            if (v == null) return new long[]{1, 0};
            v[0]++;
            return v;
        });
        if (data[0] >= MAX_ATTEMPTS) {
            data[1] = System.currentTimeMillis() + LOCK_DURATION_MS;
        }
    }

    public void resetAttempts(String username) {
        attempts.remove(username);
    }
}
