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

/** Compatibility adapter for the Phase 0 tenant-scoped resource route. */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "roseto.authorization-server.enabled", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/tenants")
public class TenantResourcesCompatibilityController {
    private final ManagementController management;

    public TenantResourcesCompatibilityController(ManagementController management) {
        this.management = management;
    }

    @PostMapping("/{tenantId}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagementController.ApiResourceResponse createResource(Authentication authentication,
                                                                    @PathVariable UUID tenantId,
                                                                    @RequestBody ManagementController.ApiResourceRequest request) {
        return management.createApiResourceForTenant(authentication, tenantId, request);
    }
}
