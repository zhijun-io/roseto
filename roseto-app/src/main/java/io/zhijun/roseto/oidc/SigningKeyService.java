package io.zhijun.roseto.oidc;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class SigningKeyService implements JwtEncoder {
    private final AtomicReference<RSAKey> current = new AtomicReference<>();
    private final List<RSAKey> retired = new ArrayList<>();
    public SigningKeyService() throws Exception { current.set(generate("roseto-1")); }
    public synchronized RSAKey current() { return current.get(); }
    public synchronized void rotate() throws Exception { retired.add(current.get()); current.set(generate("roseto-" + (retired.size() + 1))); }
    public synchronized JWKSet jwks() {
        var keys = new ArrayList<com.nimbusds.jose.jwk.JWK>();
        keys.add(current().toPublicJWK()); retired.forEach(key -> keys.add(key.toPublicJWK()));
        return new JWKSet(keys);
    }
    public synchronized List<RSAKey> verificationKeys() { var keys = new ArrayList<RSAKey>(); keys.add(current()); keys.addAll(retired); return List.copyOf(keys); }
    @Override public Jwt encode(JwtEncoderParameters parameters) { return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(current()))).encode(parameters); }
    private static RSAKey generate(String kid) throws Exception { return new RSAKeyGenerator(2048).keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).generate(); }
}
