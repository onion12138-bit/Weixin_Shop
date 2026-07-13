package com.diybracelet.mall.recommendation.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 使用环境主密钥加密 AI Provider 密钥。
 *
 * <p>主密钥在实际加解密时才解码和校验，因此本地未配置密钥时应用仍可启动；只有
 * 需要访问密钥的功能会收到不含敏感数据的受控异常。</p>
 */
@Component
public final class AiSecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ERROR =
        "AI encryption key must decode to exactly 32 bytes";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / Byte.SIZE;

    private final String encodedMasterKey;
    private final SecureRandom secureRandom;

    public AiSecretCipher(@Value("${app.ai.encryption-key:}") String encodedMasterKey) {
        this.encodedMasterKey = encodedMasterKey;
        this.secureRandom = new SecureRandom();
    }

    /**
     * 加密明文并返回 {@code base64(nonce + ciphertext + authentication tag)}。
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("AI secret must not be null");
        }

        SecretKeySpec key = masterKey();
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] stored = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, stored, 0, nonce.length);
            System.arraycopy(ciphertext, 0, stored, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Unable to encrypt AI secret");
        }
    }

    /**
     * 解密由 {@link #encrypt(String)} 生成的值，并校验 GCM 认证标签。
     */
    public String decrypt(String encodedCiphertext) {
        SecretKeySpec key = masterKey();
        if (encodedCiphertext == null) {
            throw new IllegalStateException("Unable to decrypt AI secret");
        }

        try {
            byte[] stored = Base64.getDecoder().decode(encodedCiphertext);
            if (stored.length < NONCE_BYTES + TAG_BYTES) {
                throw new IllegalArgumentException("Ciphertext is too short");
            }

            byte[] nonce = Arrays.copyOfRange(stored, 0, NONCE_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(stored, NONCE_BYTES, stored.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalStateException("Unable to decrypt AI secret");
        }
    }

    private SecretKeySpec masterKey() {
        if (encodedMasterKey == null) {
            throw new IllegalStateException(KEY_ERROR);
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedMasterKey);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(KEY_ERROR);
        }

        if (decoded.length != KEY_BYTES) {
            Arrays.fill(decoded, (byte) 0);
            throw new IllegalStateException(KEY_ERROR);
        }

        try {
            return new SecretKeySpec(decoded, "AES");
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }
}
