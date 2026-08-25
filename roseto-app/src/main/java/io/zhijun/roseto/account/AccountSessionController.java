package io.zhijun.roseto.account;

import io.zhijun.roseto.domain.session.UserSession;
import io.zhijun.roseto.domain.spi.UserSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/account/sessions")
public class AccountSessionController {
    private final UserSessionRepository sessions;
    public AccountSessionController(UserSessionRepository sessions) { this.sessions = sessions; }

    @GetMapping
    public List<SessionResponse> list(@AuthenticationPrincipal Jwt jwt) {
        var identity = identity(jwt);
        return sessions.findByUser(identity.tenantId(), identity.userId()).stream().map(SessionResponse::from).toList();
    }

    @DeleteMapping("/{sessionId}")
    public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        var identity = identity(jwt);
        var session = sessions.findByIdAndUser(identity.tenantId(), identity.userId(), sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found"));
        sessions.save(session.revoke(Instant.now()));
    }

    private static Identity identity(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        try {
            return new Identity(UUID.fromString(jwt.getClaimAsString("tenant_id")), UUID.fromString(jwt.getSubject()));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid session identity");
        }
    }

    private record Identity(UUID tenantId, UUID userId) {}
    public record SessionResponse(UUID id, String device, Instant createdAt, Instant lastSeenAt, boolean active) {
        static SessionResponse from(UserSession session) {
            return new SessionResponse(session.id(), session.device(), session.createdAt(), session.lastSeenAt(), session.active());
        }
    }
}
