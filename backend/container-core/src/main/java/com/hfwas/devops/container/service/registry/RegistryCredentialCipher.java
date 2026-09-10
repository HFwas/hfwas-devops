package com.hfwas.devops.container.service.registry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM cipher for registry credential encryption.
 * Reuses the same key as KubeconfigCipher (container.credential-key).
 */
@Component
public class RegistryCredentialCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private final byte[] keyBytes;
    private final SecureRandom random = new SecureRandom();

    public RegistryCredentialCipher(@Value("${container.credential-key:hfwas-dev-container-key-32b!}") String key) {
        try {
            this.keyBytes = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化 registry 凭据加密密钥", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) return null;
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("registry 凭据加密失败", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] packed = Base64.getDecoder().decode(encoded);
            ByteBuffer buffer = ByteBuffer.wrap(packed);
            byte[] iv = new byte[12];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("registry 凭据解密失败", e);
        }
    }
}