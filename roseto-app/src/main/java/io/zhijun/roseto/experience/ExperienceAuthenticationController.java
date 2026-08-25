package io.zhijun.roseto.experience;

import io.zhijun.roseto.domain.session.UserSession;
import io.zhijun.roseto.domain.spi.TenantRepository;
import io.zhijun.roseto.domain.spi.UserRepository;
import io.zhijun.roseto.domain.spi.UserSessionRepository;
import io.zhijun.roseto.domain.user.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/experience/auth")
public class ExperienceAuthenticationController {
    private final TenantRepository tenants;
    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder passwords;

    public ExperienceAuthenticationController(TenantRepository tenants, UserRepository users,
                                               UserSessionRepository sessions, PasswordEncoder passwords) {
        this.tenants = tenants;
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
    }

    @PostMapping("/register")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@RequestBody RegistrationRequest request) {
        if (request == null || request.tenantId() == null || blank(request.username()) || blank(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid registration request");
        }
        if (tenants.findById(request.tenantId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found");
        }
        if (users.findByTenantAndUsername(request.tenantId(), request.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "user already registered");
        }
        var user = new User(UUID.randomUUID(), request.tenantId(), request.username(),
            passwords.encode(request.password()), true, Instant.now());
        try {
            users.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "user already registered", e);
        }
        return new RegistrationResponse(user.id(), user.username());
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        if (request == null || request.tenantId() == null || blank(request.username()) || blank(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid login request");
        }
        var user = users.findByTenantAndUsername(request.tenantId(), request.username())
            .filter(value -> value.enabled() && passwords.matches(request.password(), value.passwordHash()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
        var now = Instant.now();
        var session = sessions.save(new UserSession(UUID.randomUUID(), user.tenantId(), user.id(),
            blank(request.device()) ? "unknown" : request.device(), now, now, null));
        return new LoginResponse(user.id(), session.id(), user.username());
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public record RegistrationRequest(UUID tenantId, String username, String password) {}
    public record RegistrationResponse(UUID userId, String username) {}
    public record LoginRequest(UUID tenantId, String username, String password, String device) {}
    public record LoginResponse(UUID userId, UUID sessionId, String username) {}
}
