package io.zhijun.roseto.management;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Compatibility adapter for the tenant-scoped Phase 0 Management API path.
 * Business validation and persistence remain owned by ManagementController.
 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "roseto.authorization-server.enabled", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/tenants")
public class TenantUsersCompatibilityController {
    private final ManagementController management;

    public TenantUsersCompatibilityController(ManagementController management) {
        this.management = management;
    }

    @PostMapping("/{tenantId}/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementController.UserResponse createUser(Authentication authentication,
                                                         @PathVariable UUID tenantId,
                                                         @RequestBody ManagementController.UserRequest request) {
        return management.createUser(authentication, tenantId, request);
    }
}
