package io.zhijun.roseto.domain.spi;

import io.zhijun.roseto.domain.interaction.ExperienceInteraction;

import java.util.Optional;
import java.util.UUID;

public interface ExperienceInteractionRepository {
    ExperienceInteraction save(ExperienceInteraction interaction);
    Optional<ExperienceInteraction> findByIdAndTenant(UUID tenantId, UUID interactionId);
}
