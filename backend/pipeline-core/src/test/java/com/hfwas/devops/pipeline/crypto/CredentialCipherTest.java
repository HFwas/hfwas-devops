package com.hfwas.devops.pipeline.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CredentialCipherTest {

    @Test
    void roundTripEncryptsWithoutStoringPlaintext() {
        CredentialCipher cipher = new CredentialCipher("test-key");
        String encoded = cipher.encrypt("ghp_secret");
        assertNotEquals("ghp_secret", encoded);
        assertEquals("ghp_secret", cipher.decrypt(encoded));
    }
}
