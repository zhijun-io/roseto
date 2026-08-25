package io.zhijun.roseto.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

@Configuration
@ConditionalOnProperty(name = "roseto.authorization-server.enabled", havingValue = "true")
public class AuthorizationServerSecurityConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                               io.zhijun.roseto.domain.spi.TenantRepository tenants) throws Exception {
        var authorizationServer = OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
            .with(authorizationServer, server -> server.oidc(oidc -> oidc.userInfoEndpoint(userInfo -> userInfo
                .userInfoMapper(context -> {
                    var authorization = context.getAuthorization();
                    java.util.List<String> audience;
                    String issuer;
                    try {
                        var claims = com.nimbusds.jwt.SignedJWT.parse(context.getAccessToken().getTokenValue()).getJWTClaimsSet();
                        audience = claims.getAudience();
                        issuer = claims.getIssuer();
                    } catch (java.text.ParseException e) {
                        throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                            new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"));
                    }
                    var expectedAudience = issuer == null ? null : issuer.replaceAll("/+$", "") + "/userinfo";
                    if (expectedAudience == null || audience == null || !audience.contains(expectedAudience)) {
                        throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                            new org.springframework.security.oauth2.core.OAuth2Error("invalid_token"));
                    }
                    var username = authorization.getPrincipalName();
                    var claims = new java.util.LinkedHashMap<String, Object>();
                    claims.put("sub", username);
                    return new org.springframework.security.oauth2.core.oidc.OidcUserInfo(claims);
                }))))
            .oauth2ResourceServer(resourceServer -> resourceServer
                .authenticationEntryPoint(new org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint())
                .jwt(Customizer.withDefaults()))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/login").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").permitAll())
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }
}
