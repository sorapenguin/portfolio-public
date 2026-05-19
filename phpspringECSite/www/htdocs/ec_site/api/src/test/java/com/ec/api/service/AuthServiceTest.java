package com.ec.api.service;

import com.ec.api.dto.LoginRequest;
import com.ec.api.dto.LoginResponse;
import com.ec.api.dto.RegisterRequest;
import com.ec.api.dto.VerifyRequest;
import com.ec.api.entity.AdminUser;
import com.ec.api.entity.User;
import com.ec.api.repository.AdminUserRepository;
import com.ec.api.repository.UserRepository;
import com.ec.api.security.JwtUtil;
import com.ec.api.security.LoginAttemptService;
import com.ec.api.security.TokenBlocklistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock AdminUserRepository adminUserRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock LoginAttemptService loginAttemptService;
    @Mock TokenBlocklistService tokenBlocklistService;

    @InjectMocks AuthService authService;

    // --- login: 一般ユーザー ---

    @Test
    void login_normalUser_success() {
        User user = new User();
        user.setUserName("user1");
        user.setPassword("hashed");

        LoginRequest req = new LoginRequest();
        req.setUserName("user1");
        req.setPassword("pass");

        when(loginAttemptService.isLocked("user1")).thenReturn(false);
        when(adminUserRepository.findByUserName("user1")).thenReturn(Optional.empty());
        when(userRepository.findByUserName("user1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(jwtUtil.generate("user1", "USER")).thenReturn("user-token");

        LoginResponse res = authService.login(req);

        assertThat(res.getStatus()).isEqualTo("SUCCESS");
        assertThat(res.getToken()).isEqualTo("user-token");
        assertThat(res.getRole()).isEqualTo("USER");
        assertThat(res.getSessionKey()).isNull();
        verify(loginAttemptService).resetAttempts("user1");
    }

    @Test
    void login_wrongPassword_recordsFailureAndThrows() {
        User user = new User();
        user.setUserName("user1");
        user.setPassword("hashed");

        LoginRequest req = new LoginRequest();
        req.setUserName("user1");
        req.setPassword("wrong");

        when(loginAttemptService.isLocked("user1")).thenReturn(false);
        when(adminUserRepository.findByUserName("user1")).thenReturn(Optional.empty());
        when(userRepository.findByUserName("user1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ユーザー名またはパスワードが違います");

        verify(loginAttemptService).recordFailure("user1");
        verify(loginAttemptService, never()).resetAttempts(any());
    }

    @Test
    void login_userNotFound_recordsFailureAndThrows() {
        LoginRequest req = new LoginRequest();
        req.setUserName("nobody");
        req.setPassword("pass");

        when(loginAttemptService.isLocked("nobody")).thenReturn(false);
        when(adminUserRepository.findByUserName("nobody")).thenReturn(Optional.empty());
        when(userRepository.findByUserName("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ユーザー名またはパスワードが違います");

        verify(loginAttemptService).recordFailure("nobody");
    }

    @Test
    void login_lockedAccount_throwsTooManyRequests() {
        LoginRequest req = new LoginRequest();
        req.setUserName("locked");
        req.setPassword("pass");

        when(loginAttemptService.isLocked("locked")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        // ロック中はDBアクセスなし
        verifyNoInteractions(adminUserRepository, userRepository, passwordEncoder);
    }

    // --- login: 管理者 → 2FA ---

    @Test
    void login_adminUser_returns2faRequired() {
        AdminUser admin = new AdminUser();
        admin.setUserName("admin1");
        admin.setPassword("hashed");

        LoginRequest req = new LoginRequest();
        req.setUserName("admin1");
        req.setPassword("pass");

        when(loginAttemptService.isLocked("admin1")).thenReturn(false);
        when(adminUserRepository.findByUserName("admin1")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);

        LoginResponse res = authService.login(req);

        assertThat(res.getStatus()).isEqualTo("2FA_REQUIRED");
        assertThat(res.getToken()).isNull();
        assertThat(res.getSessionKey()).isNotBlank();
        assertThat(res.getDevCode()).matches("\\d{6}");
        verify(loginAttemptService).resetAttempts("admin1");
    }

    @Test
    void login_adminWrongPassword_recordsFailureAndThrows() {
        AdminUser admin = new AdminUser();
        admin.setUserName("admin1");
        admin.setPassword("hashed");

        LoginRequest req = new LoginRequest();
        req.setUserName("admin1");
        req.setPassword("wrong");

        when(loginAttemptService.isLocked("admin1")).thenReturn(false);
        when(adminUserRepository.findByUserName("admin1")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ユーザー名またはパスワードが違います");

        verify(loginAttemptService).recordFailure("admin1");
    }

    // --- verify2fa ---

    @Test
    void verify2fa_correctCode_returnsAdminToken() {
        AdminUser admin = new AdminUser();
        admin.setUserName("admin1");
        admin.setPassword("hashed");

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUserName("admin1");
        loginReq.setPassword("pass");

        when(loginAttemptService.isLocked("admin1")).thenReturn(false);
        when(adminUserRepository.findByUserName("admin1")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(jwtUtil.generate("admin1", "ADMIN")).thenReturn("admin-token");

        LoginResponse loginRes = authService.login(loginReq);

        VerifyRequest req = new VerifyRequest();
        req.setSessionKey(loginRes.getSessionKey());
        req.setCode(loginRes.getDevCode());

        LoginResponse res = authService.verify2fa(req);

        assertThat(res.getStatus()).isEqualTo("SUCCESS");
        assertThat(res.getRole()).isEqualTo("ADMIN");
        assertThat(res.getToken()).isEqualTo("admin-token");
    }

    @Test
    void verify2fa_wrongCode_throwsUnauthorized() {
        AdminUser admin = new AdminUser();
        admin.setUserName("admin1");
        admin.setPassword("hashed");

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUserName("admin1");
        loginReq.setPassword("pass");

        when(loginAttemptService.isLocked("admin1")).thenReturn(false);
        when(adminUserRepository.findByUserName("admin1")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);

        LoginResponse loginRes = authService.login(loginReq);
        String actualCode = loginRes.getDevCode();

        if (!"000000".equals(actualCode)) {
            VerifyRequest req = new VerifyRequest();
            req.setSessionKey(loginRes.getSessionKey());
            req.setCode("000000");

            assertThatThrownBy(() -> authService.verify2fa(req))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("認証コードが違います");
        }
    }

    @Test
    void verify2fa_expiredSession_throwsUnauthorized() {
        VerifyRequest req = new VerifyRequest();
        req.setSessionKey("nonexistent-session-key");
        req.setCode("123456");

        assertThatThrownBy(() -> authService.verify2fa(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("認証コードの有効期限が切れました");
    }

    @Test
    void verify2fa_sessionConsumedAfterSuccess_cannotReuseSession() {
        AdminUser admin = new AdminUser();
        admin.setUserName("admin1");
        admin.setPassword("hashed");

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUserName("admin1");
        loginReq.setPassword("pass");

        when(loginAttemptService.isLocked("admin1")).thenReturn(false);
        when(adminUserRepository.findByUserName("admin1")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(jwtUtil.generate("admin1", "ADMIN")).thenReturn("admin-token");

        LoginResponse loginRes = authService.login(loginReq);

        VerifyRequest req = new VerifyRequest();
        req.setSessionKey(loginRes.getSessionKey());
        req.setCode(loginRes.getDevCode());

        authService.verify2fa(req);

        assertThatThrownBy(() -> authService.verify2fa(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("認証コードの有効期限が切れました");
    }

    // --- register ---

    @Test
    void register_newUser_savesWithHashedPassword() {
        RegisterRequest req = new RegisterRequest();
        req.setUserName("newuser");
        req.setPassword("password123");

        when(userRepository.existsByUserName("newuser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");

        authService.register(req);

        verify(userRepository).save(argThat(u ->
                "newuser".equals(u.getUserName()) &&
                "hashed-password".equals(u.getPassword())
        ));
    }

    @Test
    void register_duplicateUsername_throwsConflict() {
        RegisterRequest req = new RegisterRequest();
        req.setUserName("existing");
        req.setPassword("password123");

        when(userRepository.existsByUserName("existing")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("すでに使われています");

        verify(userRepository, never()).save(any());
    }

    // --- logout ---

    @Test
    void logout_blocksToken() {
        when(jwtUtil.getExpirationMs("valid-token")).thenReturn(9999999999999L);

        authService.logout("valid-token");

        verify(tokenBlocklistService).block("valid-token", 9999999999999L);
    }
}
