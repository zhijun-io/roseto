package io.zhijun.roseto.sample;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SampleResourceController {
    @GetMapping("/api/sample/read")
    @PreAuthorize("@resourceAudience.has(authentication) and hasAuthority('SCOPE_api:read')")
    public Map<String, String> read() {
        return Map.of("resource", "sample", "access", "read");
    }
}
