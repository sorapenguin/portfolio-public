package com.ec.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TokenBlocklistServiceTest {

    private TokenBlocklistService service;

    @BeforeEach
    void setUp() {
        service = new TokenBlocklistService();
    }

    @Test
    void isBlocked_unknownToken_returnsFalse() {
        assertThat(service.isBlocked("unknown-token")).isFalse();
    }

    @Test
    void block_tokenBecomesBlocked() {
        long futureExpiry = System.currentTimeMillis() + 3_600_000L;
        service.block("my-token", futureExpiry);
        assertThat(service.isBlocked("my-token")).isTrue();
    }

    @Test
    void isBlocked_expiredToken_returnsFalse() {
        long pastExpiry = System.currentTimeMillis() - 1L;
        service.block("expired-token", pastExpiry);
        assertThat(service.isBlocked("expired-token")).isFalse();
    }

    @Test
    void block_differentTokens_independentlyTracked() {
        long future = System.currentTimeMillis() + 3_600_000L;
        service.block("token-a", future);

        assertThat(service.isBlocked("token-a")).isTrue();
        assertThat(service.isBlocked("token-b")).isFalse();
    }

    @Test
    void block_sameTokenTwice_remainsBlocked() {
        long future = System.currentTimeMillis() + 3_600_000L;
        service.block("token", future);
        service.block("token", future);
        assertThat(service.isBlocked("token")).isTrue();
    }

    @Test
    void purgeExpired_removesStaleEntriesOnNextBlock() {
        // 期限切れトークンをブロック
        long past = System.currentTimeMillis() - 1L;
        service.block("stale-token", past);

        // 新しいトークンをブロックすると内部でpurgeが走る
        service.block("new-token", System.currentTimeMillis() + 3_600_000L);

        // 期限切れは自動削除され isBlocked=false
        assertThat(service.isBlocked("stale-token")).isFalse();
        assertThat(service.isBlocked("new-token")).isTrue();
    }
}
