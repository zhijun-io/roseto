package io.zhijun.roseto.account;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.UNAUTHORIZED);
        var response = new LinkedHashMap<String, Object>();
        response.put("sub", jwt.getSubject());
        response.put("tenant_id", jwt.getClaimAsString("tenant_id"));
        response.put("scope", jwt.getClaimAsString("scope"));
        List<String> audience = jwt.getAudience();
        if (audience != null && !audience.isEmpty()) response.put("aud", audience);
        putIfPresent(response, "preferred_username", jwt.getClaimAsString("preferred_username"));
        putIfPresent(response, "email", jwt.getClaimAsString("email"));
        putIfPresent(response, "organization_id", jwt.getClaimAsString("organization_id"));
        return response;
    }

    private static void putIfPresent(Map<String, Object> response, String name, String value) {
        if (value != null && !value.isBlank()) response.put(name, value);
    }
}
