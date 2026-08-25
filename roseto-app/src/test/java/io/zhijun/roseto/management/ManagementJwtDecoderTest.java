package io.zhijun.roseto.management;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ManagementJwtDecoderTest {
    private static final String SECRET = "change-me-management-secret-at-least-32-bytes";

    @Test
    void rejectsTokenWithWrongAudience() {
        var config = new ManagementConfiguration();
        var decoder = config.managementJwtDecoder(SECRET);
        var encoder = config.managementJwtEncoder(SECRET);
        var claims = JwtClaimsSet.builder().issuer("roseto").subject("account")
            .audience(List.of("wrong-audience")).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        var headers = JwsHeader.with(MacAlgorithm.HS256).build();
        var token = encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
        assertThrows(JwtException.class, () -> decoder.decode(token));
    }
}
