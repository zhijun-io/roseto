package io.zhijun.roseto.domain.spi;

import io.zhijun.roseto.domain.api.ApiResource;
import java.util.Optional;
import java.util.UUID;

public interface ApiResourceRepository {
    ApiResource save(ApiResource resource);
    void addScope(UUID resourceId, String scope);
    boolean hasScope(UUID resourceId, String scope);
    Optional<ApiResource> findByIndicator(String indicator);
    java.util.List<String> scopes(UUID resourceId);
    void removeScope(UUID resourceId, String scope);
}
