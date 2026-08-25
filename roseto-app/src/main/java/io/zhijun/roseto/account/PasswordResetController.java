package io.zhijun.roseto.account;

import io.zhijun.roseto.domain.spi.UserRepository;
import io.zhijun.roseto.domain.spi.VerificationCodeSender;
import io.zhijun.roseto.domain.spi.VerificationRepository;
import io.zhijun.roseto.domain.verification.VerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/account/password")
public class PasswordResetController {
    private final UserRepository users;
    private final VerificationService verifications;
    private final PasswordEncoder passwords;

    public PasswordResetController(UserRepository users, VerificationRepository verifications,
                                   VerificationCodeSender sender, PasswordEncoder passwords) {
        this.users = users;
        this.verifications = new VerificationService(verifications, sender, Clock.systemUTC());
        this.passwords = passwords;
    }

    @PostMapping("/reset-request")
    public ResetRequestResponse request(@RequestBody ResetRequest request) {
        if (request == null || request.tenantId() == null || blank(request.username())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid password reset request");
        }
        var user = users.findByTenantAndUsername(request.tenantId(), request.username())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        var result = verifications.issue(user.tenantId(), user.id(), "password_reset", user.username(),
            Duration.ofMinutes(10), 5);
        return new ResetRequestResponse(result.verificationId(), result.expiresAt());
    }

    @PostMapping("/reset")
    public void reset(@RequestBody ResetPasswordRequest request) {
        if (request == null || request.tenantId() == null || request.verificationId() == null
            || blank(request.code()) || blank(request.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid password reset request");
        }
        var result = verifications.verify(request.tenantId(), request.verificationId(), request.code());
        if (!result.accepted() || result.verification().userId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid verification");
        }
        var user = users.findById(result.verification().userId())
            .filter(value -> value.tenantId().equals(request.tenantId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        users.save(new io.zhijun.roseto.domain.user.User(user.id(), user.tenantId(), user.username(),
            passwords.encode(request.newPassword()), user.enabled(), user.createdAt()));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public record ResetRequest(UUID tenantId, String username) {}
    public record ResetRequestResponse(UUID verificationId, java.time.Instant expiresAt) {}
    public record ResetPasswordRequest(UUID tenantId, UUID verificationId, String code, String newPassword) {}
}
