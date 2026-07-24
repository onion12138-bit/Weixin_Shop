package com.diybracelet.mall.catalog.visual.application;

import com.diybracelet.mall.file.domain.AssetFile;
import com.diybracelet.mall.file.domain.FileStorage;
import com.diybracelet.mall.file.domain.StoredFile;
import com.diybracelet.mall.shared.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisualAssetValidatorTest {

    private final InMemoryFileStorage storage = new InMemoryFileStorage();
    private VisualAssetValidator validator;

    @BeforeEach
    void setUp() {
        validator = new VisualAssetValidator(storage);
    }

    @Test
    void shouldAcceptRgbaPngAtRequiredDimensions() {
        validator.validateTray(activeAsset(160, 160, "image/png", true));
        validator.validateCanvas(activeAsset(512, 512, "image/png", true));
    }

    @Test
    void shouldRejectOpaqueOrOversizedVisuals() {
        assertThatThrownBy(() -> validator.validateCanvas(
                activeAsset(512, 512, "image/png", false)
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("透明通道");

        assertThatThrownBy(() -> validator.validateCanvas(
                activeAssetWithSize(500_001L)
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("500 KB");
    }

    @Test
    void shouldAcceptExtendedAlphaWebpAtRequiredDimensions() {
        validator.validateTray(activeWebpAsset(webpExtended(160, 160)));
        validator.validateCanvas(activeWebpAsset(webpExtended(512, 512)));
    }

    @Test
    void shouldAcceptLosslessAlphaWebpAtRequiredDimensions() {
        validator.validateTray(activeWebpAsset(webpLossless(160, 160)));
        validator.validateCanvas(activeWebpAsset(webpLossless(512, 512)));
    }

    private AssetFile activeAsset(
            int width,
            int height,
            String contentType,
            boolean alpha
    ) {
        String storageKey = "test-" + storage.size() + ".png";
        storage.put(storageKey, png(width, height, alpha));
        AssetFile asset = activeAssetWithSize(storage.read(storageKey).length);
        asset.setStorageKey(storageKey);
        asset.setContentType(contentType);
        return asset;
    }

    private AssetFile activeAssetWithSize(long sizeBytes) {
        AssetFile asset = new AssetFile();
        asset.setStorageKey("oversized.png");
        asset.setContentType("image/png");
        asset.setSizeBytes(sizeBytes);
        asset.setStatus("ACTIVE");
        return asset;
    }

    private AssetFile activeWebpAsset(byte[] content) {
        String storageKey = "test-" + storage.size() + ".webp";
        storage.put(storageKey, content);
        AssetFile asset = activeAssetWithSize(content.length);
        asset.setStorageKey(storageKey);
        asset.setContentType("image/webp");
        return asset;
    }

    private byte[] png(int width, int height, boolean alpha) {
        ByteBuffer bytes = ByteBuffer.allocate(33).order(ByteOrder.BIG_ENDIAN);
        bytes.put(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        bytes.putInt(13);
        bytes.put(new byte[] {0x49, 0x48, 0x44, 0x52});
        bytes.putInt(width);
        bytes.putInt(height);
        bytes.put((byte) 8);
        bytes.put((byte) (alpha ? 6 : 2));
        bytes.put((byte) 0);
        bytes.put((byte) 0);
        bytes.put((byte) 0);
        bytes.putInt(0);
        return bytes.array();
    }

    private byte[] webpExtended(int width, int height) {
        byte[] bytes = webpHeader("VP8X", 10);
        bytes[20] = 0x10;
        put24LittleEndian(bytes, 24, width - 1);
        put24LittleEndian(bytes, 27, height - 1);
        return bytes;
    }

    private byte[] webpLossless(int width, int height) {
        byte[] bytes = webpHeader("VP8L", 5);
        int encodedWidth = width - 1;
        int encodedHeight = height - 1;
        bytes[20] = 0x2f;
        bytes[21] = (byte) encodedWidth;
        bytes[22] = (byte) ((encodedWidth >>> 8 & 0x3f)
                | ((encodedHeight & 0x03) << 6));
        bytes[23] = (byte) (encodedHeight >>> 2);
        bytes[24] = (byte) (((encodedHeight >>> 10) & 0x0f) | 0x10);
        return bytes;
    }

    private byte[] webpHeader(String chunkType, int chunkSize) {
        byte[] bytes = new byte[20 + chunkSize];
        bytes[0] = 'R';
        bytes[1] = 'I';
        bytes[2] = 'F';
        bytes[3] = 'F';
        bytes[8] = 'W';
        bytes[9] = 'E';
        bytes[10] = 'B';
        bytes[11] = 'P';
        for (int index = 0; index < 4; index++) {
            bytes[12 + index] = (byte) chunkType.charAt(index);
        }
        bytes[16] = (byte) chunkSize;
        return bytes;
    }

    private void put24LittleEndian(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
    }

    private static final class InMemoryFileStorage implements FileStorage {
        private final Map<String, byte[]> content = new HashMap<>();

        @Override
        public StoredFile store(String originalName, String contentType, byte[] bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] read(String storageKey) {
            return content.get(storageKey);
        }

        void put(String storageKey, byte[] bytes) {
            content.put(storageKey, bytes);
        }

        int size() {
            return content.size();
        }
    }
}
