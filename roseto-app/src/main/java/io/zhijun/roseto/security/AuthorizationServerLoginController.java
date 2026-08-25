package io.zhijun.roseto.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "roseto.authorization-server.enabled", havingValue = "true")
class AuthorizationServerLoginController {
    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> loginPage() {
        return ResponseEntity.ok("<html><body><form method=post action='/login'>"
            + "<label>Username<input name=username></label>"
            + "<label>Password<input type=password name=password></label>"
            + "<button type=submit>Sign in</button></form></body></html>");
    }
}
