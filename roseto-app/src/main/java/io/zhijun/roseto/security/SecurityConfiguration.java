package io.zhijun.roseto.security;
 

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth ->
                auth.requestMatchers("/actuator/health/**", "/api/management/bootstrap", "/api/management/token",
                    "/api/experience/verification/**", "/api/experience/auth/**", "/api/experience/interaction",
                    "/api/experience/submit", "/api/account/password/**",
                    "/oauth2/authorize", "/oauth2/authorize/login", "/oauth2/token", "/oauth2/revoke", "/oauth2/logout",
                    "/oauth2/jwks", "/.well-known/openid-configuration", "/login").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").permitAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
            .build();
    }
}
