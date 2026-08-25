package io.zhijun.roseto.domain.spi;

import io.zhijun.roseto.domain.tenant.Tenant;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(UUID id);

    Optional<Tenant> findByPublicKey(String publicKey);
    Optional<Tenant> findFirst();

    long count();
}
