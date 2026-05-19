package com.ec.api.security;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * IP単位のブルートフォース対策。
 * ログイン失敗・2FAコード失敗を合算し、IP_MAX_FAILURES 回で IP_LOCK_MS ロック。
 * インメモリ管理のためアプリ再起動でリセットされる（ポートフォリオ用途では許容）。
 */
@Service
public class IpBlockService {

    public static final int IP_MAX_FAILURES = 10;
    public static final long IP_LOCK_MS = 60 * 60 * 1000L; // 1時間

    // ip -> [failCount, blockUntilMs]
    private final ConcurrentHashMap<String, long[]> records = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip) {
        long[] data = records.get(ip);
        if (data == null || data[0] < IP_MAX_FAILURES) return false;
        if (System.currentTimeMillis() > data[1]) {
            records.remove(ip);
            return false;
        }
        return true;
    }

    public void recordFailure(String ip) {
        long[] data = records.compute(ip, (k, v) -> {
            if (v == null) return new long[]{1, 0};
            v[0]++;
            return v;
        });
        if (data[0] >= IP_MAX_FAILURES) {
            data[1] = System.currentTimeMillis() + IP_LOCK_MS;
        }
    }

    public void reset(String ip) {
        records.remove(ip);
    }
}
