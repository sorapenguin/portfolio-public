package com.ec.api.security;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * JWTログアウト用ブロックリスト。
 * ログアウト済みトークンをインメモリで管理し、期限切れエントリを遅延削除する。
 * JWTは本来ステートレスで「無効化」できないため、このブロックリストで補完する。
 */
@Service
public class TokenBlocklistService {

    // token -> expiryMs（JWTの有効期限と同じ）
    private final ConcurrentHashMap<String, Long> blocklist = new ConcurrentHashMap<>();

    public void block(String token, long expiryMs) {
        purgeExpired();
        blocklist.put(token, expiryMs);
    }

    public boolean isBlocked(String token) {
        Long expiry = blocklist.get(token);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            blocklist.remove(token);
            return false;
        }
        return true;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        blocklist.entrySet().removeIf(e -> now > e.getValue());
    }
}
