package io.zhijun.roseto.experience;

import io.zhijun.roseto.domain.spi.ExperienceInteractionRepository;
import io.zhijun.roseto.domain.spi.VerificationCodeSender;
import io.zhijun.roseto.domain.spi.VerificationRepository;
import io.zhijun.roseto.domain.verification.VerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.UUID;

@RestController
@RequestMapping("/api/experience/verification")
public class ExperienceVerificationController {
    private final VerificationService verifications;
    private final VerificationRepository repository;
    private final ExperienceInteractionRepository interactions;

    public ExperienceVerificationController(VerificationRepository repository, VerificationCodeSender sender,
                                             ExperienceInteractionRepository interactions) {
        this.repository = repository;
        this.interactions = interactions;
        this.verifications = new VerificationService(repository, sender, Clock.systemUTC());
    }

    @PostMapping("/{type}")
    public VerificationResponse verify(@PathVariable String type, @RequestBody VerificationRequest request) {
        if (request == null || request.tenantId() == null || request.verificationId() == null
            || request.code() == null || request.code().isBlank() || type.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid verification request");
        }
        try {
            var current = repository.findByIdAndTenant(request.tenantId(), request.verificationId())
                .orElseThrow(() -> new IllegalArgumentException("verification not found"));
            if (request.interactionId() != null && !request.interactionId().equals(current.interactionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "interaction mismatch");
            }
            var result = verifications.verify(request.tenantId(), request.verificationId(), request.code());
            if (!result.accepted()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.error());
            return new VerificationResponse("verified", result.verification().purpose());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "verification not found");
        }
    }

    @PostMapping("/{type}/send")
    public VerificationSendResponse send(@PathVariable String type, @RequestBody VerificationSendRequest request) {
        if (request == null || request.tenantId() == null || request.identifier() == null
            || request.identifier().isBlank() || type.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid verification request");
        }
        if (request.interactionId() != null
            && interactions.findByIdAndTenant(request.tenantId(), request.interactionId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "interaction not found");
        }
        var purpose = request.purpose() == null || request.purpose().isBlank() ? type : request.purpose();
        var result = verifications.issue(request.tenantId(), request.userId(), request.interactionId(), purpose,
            request.identifier(), java.time.Duration.ofMinutes(10), 5);
        return new VerificationSendResponse(result.verificationId(), result.expiresAt());
    }

    public record VerificationRequest(UUID tenantId, UUID verificationId, String code, UUID interactionId) {}
    public record VerificationSendRequest(UUID tenantId, UUID userId, String identifier, String purpose,
                                          UUID interactionId) {}
    public record VerificationResponse(String status, String purpose) {}
    public record VerificationSendResponse(UUID verificationId, java.time.Instant expiresAt) {}
}
