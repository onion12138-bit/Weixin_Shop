package com.diybracelet.mall.recommendation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AiSecretCipherTest {

    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(
        "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
    );

    @Test
    void shouldUseAUniqueNonceAndDecryptBothCiphertexts() {
        AiSecretCipher cipher = new AiSecretCipher(MASTER_KEY);

        String first = cipher.encrypt("sensitive-test-value");
        String second = cipher.encrypt("sensitive-test-value");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("sensitive-test-value");
        assertThat(cipher.decrypt(second)).isEqualTo("sensitive-test-value");
    }

    @Test
    void shouldRejectTamperedCiphertextWithoutExposingSensitiveValues() {
        String plaintext = "sensitive-test-value";
        AiSecretCipher cipher = new AiSecretCipher(MASTER_KEY);
        String encrypted = cipher.encrypt(plaintext);
        byte[] tamperedBytes = Base64.getDecoder().decode(encrypted);
        tamperedBytes[tamperedBytes.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(tamperedBytes);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unable to decrypt AI secret")
            .satisfies(error -> assertThat(error.toString())
                .doesNotContain(plaintext, MASTER_KEY, encrypted, tampered));
    }

    @Test
    void shouldRejectNullCiphertextWithAControlledSecretFreeException() {
        AiSecretCipher cipher = new AiSecretCipher(MASTER_KEY);

        assertThatThrownBy(() -> cipher.decrypt(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unable to decrypt AI secret")
            .hasNoCause();
    }

    @Test
    void shouldValidateTheMasterKeyOnlyWhenCryptographyIsRequested() {
        AiSecretCipher emptyKeyCipher = new AiSecretCipher("");
        AiSecretCipher nullKeyCipher = new AiSecretCipher(null);
        String invalidKey = Base64.getEncoder().encodeToString(new byte[31]);
        AiSecretCipher wrongLengthCipher = new AiSecretCipher(invalidKey);

        assertThat(emptyKeyCipher.toString()).doesNotContain(MASTER_KEY);
        assertThatThrownBy(() -> emptyKeyCipher.encrypt("secret"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AI encryption key must decode to exactly 32 bytes")
            .satisfies(error -> assertThat(error.toString()).doesNotContain("secret"));
        assertThatThrownBy(() -> nullKeyCipher.encrypt("secret"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AI encryption key must decode to exactly 32 bytes")
            .satisfies(error -> assertThat(error.toString()).doesNotContain("secret"));
        assertThatThrownBy(() -> wrongLengthCipher.decrypt(
            Base64.getEncoder().encodeToString(new byte[28])))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("AI encryption key must decode to exactly 32 bytes")
            .satisfies(error -> assertThat(error.toString()).doesNotContain(invalidKey));
    }
}
