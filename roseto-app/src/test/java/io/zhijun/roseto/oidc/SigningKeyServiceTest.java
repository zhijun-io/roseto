package io.zhijun.roseto.oidc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SigningKeyServiceTest {
    @Test
    void rotationKeepsOldVerificationKeyAndPublishesNewKid() throws Exception {
        var keys = new SigningKeyService();
        assertEquals("roseto-1", keys.current().getKeyID());
        assertEquals(1, keys.jwks().getKeys().size());
        keys.rotate();
        assertEquals("roseto-2", keys.current().getKeyID());
        assertEquals(2, keys.jwks().getKeys().size());
        assertEquals(2, keys.verificationKeys().size());
        assertEquals("roseto-1", keys.verificationKeys().get(1).getKeyID());
    }
}
