package io.zhijun.roseto.domain.spi;

import io.zhijun.roseto.domain.management.ManagementAccount;

import java.util.Optional;

public interface ManagementAccountRepository {

    ManagementAccount save(ManagementAccount account);

    Optional<ManagementAccount> findByUsername(String username);

    long count();
}
