package io.zhijun.roseto.experience;

import io.zhijun.roseto.authorization.AuthorizationController;
import io.zhijun.roseto.domain.interaction.ExperienceInteraction;
import io.zhijun.roseto.domain.session.UserSession;
import io.zhijun.roseto.domain.spi.ExperienceInteractionRepository;
import io.zhijun.roseto.domain.spi.TenantRepository;
import io.zhijun.roseto.domain.spi.UserRepository;
import io.zhijun.roseto.domain.spi.UserSessionRepository;
import io.zhijun.roseto.domain.spi.VerificationRepository;
import io.zhijun.roseto.domain.user.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/experience")
public class ExperienceInteractionController {
    private final TenantRepository tenants;
    private final ExperienceInteractionRepository interactions;
    private final UserRepository users;
    private final AuthorizationController authorizations;
    private final VerificationRepository verifications;
    private final UserSessionRepository sessions;
    private final PasswordEncoder passwords;
    public ExperienceInteractionController(TenantRepository tenants, ExperienceInteractionRepository interactions,
                                            UserRepository users, UserSessionRepository sessions,
                                            PasswordEncoder passwords, AuthorizationController authorizations,
                                            VerificationRepository verifications) {
        this.tenants = tenants;
        this.interactions = interactions;
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.authorizations = authorizations;
        this.verifications = verifications;
    }

    @PostMapping("/interaction")
    @ResponseStatus(HttpStatus.CREATED)
    public InteractionResponse create(@RequestBody InteractionRequest request) {
        if (request == null || request.tenantId() == null || blank(request.identifier())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid interaction request");
        }
        requireTenant(request.tenantId());
        if (request.interactionId() != null) {
            var existing = interactions.findByIdAndTenant(request.tenantId(), request.interactionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "interaction not found"));
            return response(existing, null, null, null);
        }
        var now = Instant.now();
        var interaction = new ExperienceInteraction(UUID.randomUUID(), request.tenantId(), request.identifier(),
            blank(request.mode()) ? "sign-in" : request.mode(), ExperienceInteraction.Status.CREATED, now, now,
            request.clientId(), request.redirectUri(), request.codeChallenge(), request.nonce(),
            blank(request.scope()) ? "openid" : request.scope(), request.resource());
        return response(interactions.save(interaction), null, null, null);
    }

    @GetMapping("/interaction")
    public InteractionResponse get(@RequestParam UUID tenantId, @RequestParam UUID interactionId) {
        requireTenant(tenantId);
        return interactions.findByIdAndTenant(tenantId, interactionId)
            .map(value -> response(value, null, null, null))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "interaction not found"));
    }

    @PostMapping("/submit")
    public InteractionResponse submit(@RequestBody SubmitRequest request) {
        if (request == null || request.tenantId() == null || request.interactionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid submit request");
        }
        requireTenant(request.tenantId());
        var interaction = interactions.findByIdAndTenant(request.tenantId(), request.interactionId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "interaction not found"));
        if (interaction.status() == ExperienceInteraction.Status.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "interaction already completed");
        }
        if ("sign-up".equalsIgnoreCase(interaction.mode()) && request.verificationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verification required");
        }
        if (request.verificationId() != null) {
            var verification = verifications.findByIdAndTenant(request.tenantId(), request.verificationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "verification not found"));
            if (!request.interactionId().equals(verification.interactionId())
                || verification.status() != io.zhijun.roseto.domain.verification.Verification.Status.CONSUMED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "verification not completed");
            }
        }
        if (blank(request.password())) {
            return response(interactions.save(interaction.complete(Instant.now())), null, null, null);
        }
        var user = resolveUser(interaction, request);
        var session = sessions.save(new UserSession(UUID.randomUUID(), user.tenantId(), user.id(),
            blank(request.device()) ? "browser" : request.device(), Instant.now(), Instant.now(), null));
        var completed = interactions.save(interaction.complete(Instant.now()));
        String code = null;
        if (!blank(completed.clientId()) && !blank(completed.redirectUri()) && !blank(completed.codeChallenge())) {
            code = authorizations.createAuthorizationCode(completed.tenantId(), completed.clientId(), user.id(),
                completed.redirectUri(), completed.scope(), completed.resource(), completed.codeChallenge(), completed.nonce());
        }
        return response(completed, user.id(), session.id(), code);
    }

    private User resolveUser(ExperienceInteraction interaction, SubmitRequest request) {
        var existing = users.findByTenantAndUsername(interaction.tenantId(), interaction.identifier());
        if ("sign-up".equalsIgnoreCase(interaction.mode())) {
            if (existing.isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "user already registered");
            try {
                return users.save(new User(UUID.randomUUID(), interaction.tenantId(), interaction.identifier(),
                    passwords.encode(request.password()), true, Instant.now()));
            } catch (DataIntegrityViolationException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "user already registered", e);
            }
        }
        return existing.filter(value -> value.enabled() && passwords.matches(request.password(), value.passwordHash()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
    }

    private void requireTenant(UUID tenantId) {
        if (tenants.findById(tenantId).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "tenant not found");
    }

    private static InteractionResponse response(ExperienceInteraction interaction, UUID userId, UUID sessionId, String code) {
        return new InteractionResponse(interaction.id(), interaction.tenantId(), interaction.identifier(), interaction.mode(),
            interaction.status().name(), interaction.createdAt(), interaction.updatedAt(), userId, sessionId, code);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record InteractionRequest(UUID tenantId, UUID interactionId, String identifier, String mode,
                                     String clientId, String redirectUri, String codeChallenge, String nonce,
                                     String scope, String resource) {
        public InteractionRequest(UUID tenantId, UUID interactionId, String identifier, String mode) {
            this(tenantId, interactionId, identifier, mode, null, null, null, null, "openid", null);
        }
    }
    public record SubmitRequest(UUID tenantId, UUID interactionId, String password, String device, UUID verificationId) {
        public SubmitRequest(UUID tenantId, UUID interactionId, String password, String device) {
            this(tenantId, interactionId, password, device, null);
        }
    }
    public record InteractionResponse(UUID interactionId, UUID tenantId, String identifier, String mode,
                                      String status, Instant createdAt, Instant updatedAt,
                                      UUID userId, UUID sessionId, String authorizationCode) {}
}
