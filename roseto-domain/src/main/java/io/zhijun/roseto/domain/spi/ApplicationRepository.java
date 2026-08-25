package io.zhijun.roseto.domain.spi;

import io.zhijun.roseto.domain.application.Application;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository {
    Application save(Application application);
    void addRedirectUri(UUID applicationId, String redirectUri);
    java.util.List<String> redirectUris(UUID applicationId);
    boolean hasRedirectUri(UUID applicationId, String redirectUri);
    Optional<Application> findByClientId(String clientId);
    Optional<Application> findById(UUID id);
    void addApiResource(UUID applicationId, UUID resourceId);
    boolean hasApiResource(UUID applicationId, UUID resourceId);
}
