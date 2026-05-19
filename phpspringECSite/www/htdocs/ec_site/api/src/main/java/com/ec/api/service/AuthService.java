package com.ec.api.service;

import com.ec.api.dto.LoginRequest;
import com.ec.api.dto.LoginResponse;
import com.ec.api.dto.RegisterRequest;
import com.ec.api.dto.VerifyRequest;
import com.ec.api.entity.AdminUser;
import com.ec.api.entity.User;
import com.ec.api.repository.AdminUserRepository;
import com.ec.api.repository.UserRepository;
import com.ec.api.security.IpBlockService;
import com.ec.api.security.JwtUtil;
import com.ec.api.security.LoginAttemptService;
import com.ec.api.security.TokenBlocklistService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlocklistService tokenBlocklistService;
    private final IpBlockService ipBlockService;

    // 2FA: sessionKey -> {code, expiry(ms), username, failCount}
    private final Map<String, long[]>  twoFaExpiry    = new ConcurrentHashMap<>();
    private final Map<String, String>  twoFaCode      = new ConcurrentHashMap<>();
    private final Map<String, String>  twoFaUser      = new ConcurrentHashMap<>();
    private final Map<String, Integer> twoFaAttempts  = new ConcurrentHashMap<>();

    private static final int TWO_FA_MAX_ATTEMPTS = 3;

    public AuthService(UserRepository userRepository,
                       AdminUserRepository adminUserRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       LoginAttemptService loginAttemptService,
                       TokenBlocklistService tokenBlocklistService,
                       IpBlockService ipBlockService) {
        this.userRepository = userRepository;
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.loginAttemptService = loginAttemptService;
        this.tokenBlocklistService = tokenBlocklistService;
        this.ipBlockService = ipBlockService;
    }

    public LoginResponse login(LoginRequest req, String ip) {
        if (ipBlockService.isBlocked(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "試行回数が多すぎます。しばらく時間をおいて再試行してください");
        }
        if (loginAttemptService.isLocked(req.getUserName())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "ログイン試行回数が多すぎます。しばらく時間をおいて再試行してください");
        }

        var adminOpt = adminUserRepository.findByUserName(req.getUserName());
        if (adminOpt.isPresent()) {
            AdminUser admin = adminOpt.get();
            if (!passwordEncoder.matches(req.getPassword(), admin.getPassword())) {
                loginAttemptService.recordFailure(req.getUserName());
                ipBlockService.recordFailure(ip);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザー名またはパスワードが違います");
            }
            loginAttemptService.resetAttempts(req.getUserName());

            String sessionKey = UUID.randomUUID().toString();
            String code = String.format("%06d", (int)(Math.random() * 1_000_000));
            twoFaCode.put(sessionKey, code);
            twoFaUser.put(sessionKey, admin.getUserName());
            twoFaExpiry.put(sessionKey, new long[]{System.currentTimeMillis() + 600_000});
            twoFaAttempts.put(sessionKey, 0);

            // ポートフォリオ用: コンソールのみに出力（レスポンスには含めない）
            System.out.printf("[2FA] user=%s code=%s%n", admin.getUserName(), code);

            return new LoginResponse("2FA_REQUIRED", null, null, sessionKey);
        }

        User user = userRepository.findByUserName(req.getUserName())
                .orElseGet(() -> {
                    loginAttemptService.recordFailure(req.getUserName());
                    ipBlockService.recordFailure(ip);
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザー名またはパスワードが違います");
                });

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(req.getUserName());
            ipBlockService.recordFailure(ip);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ユーザー名またはパスワードが違います");
        }

        loginAttemptService.resetAttempts(req.getUserName());
        ipBlockService.reset(ip);
        String token = jwtUtil.generate(user.getUserName(), "USER");
        return new LoginResponse("SUCCESS", token, "USER", null);
    }

    public LoginResponse verify2fa(VerifyRequest req, String ip) {
        if (ipBlockService.isBlocked(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "試行回数が多すぎます。しばらく時間をおいて再試行してください");
        }

        String sessionKey = req.getSessionKey();
        long[] expiry = twoFaExpiry.get(sessionKey);

        if (expiry == null || System.currentTimeMillis() > expiry[0]) {
            invalidate2faSession(sessionKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "認証コードの有効期限が切れました。再度ログインしてください");
        }

        if (!req.getCode().equals(twoFaCode.get(sessionKey))) {
            int attempts = twoFaAttempts.merge(sessionKey, 1, Integer::sum);
            ipBlockService.recordFailure(ip);

            if (attempts >= TWO_FA_MAX_ATTEMPTS) {
                invalidate2faSession(sessionKey);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "認証コードの入力に複数回失敗しました。セキュリティのため再度ログインしてください");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "認証コードが違います（残り " + (TWO_FA_MAX_ATTEMPTS - attempts) + " 回）");
        }

        String username = twoFaUser.get(sessionKey);
        invalidate2faSession(sessionKey);
        ipBlockService.reset(ip);

        String token = jwtUtil.generate(username, "ADMIN");
        return new LoginResponse("SUCCESS", token, "ADMIN", null);
    }

    public void register(RegisterRequest req) {
        if (userRepository.existsByUserName(req.getUserName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "このユーザー名はすでに使われています");
        }
        User user = new User();
        user.setUserName(req.getUserName());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setCreateDate(LocalDate.now());
        user.setUpdateDate(LocalDate.now());
        userRepository.save(user);
    }

    public void logout(String token) {
        tokenBlocklistService.block(token, jwtUtil.getExpirationMs(token));
    }

    private void invalidate2faSession(String sessionKey) {
        twoFaCode.remove(sessionKey);
        twoFaUser.remove(sessionKey);
        twoFaExpiry.remove(sessionKey);
        twoFaAttempts.remove(sessionKey);
    }
}
