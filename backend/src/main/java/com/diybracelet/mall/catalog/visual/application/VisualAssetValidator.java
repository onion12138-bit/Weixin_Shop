package com.diybracelet.mall.catalog.visual.application;

import com.diybracelet.mall.file.domain.AssetFile;
import com.diybracelet.mall.file.domain.FileStorage;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** 以受限图片头检查人工视觉素材的尺寸和透明通道。 */
@Component
public class VisualAssetValidator {

    private static final long MAX_SIZE_BYTES = 500_000L;
    private static final int HEADER_LIMIT = 64;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private final FileStorage fileStorage;

    public VisualAssetValidator(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    public void validateTray(AssetFile asset) {
        validate(asset, 160, 160, "托盘图");
    }

    public void validateCanvas(AssetFile asset) {
        validate(asset, 512, 512, "画布图");
    }

    private void validate(AssetFile asset, int width, int height, String label) {
        if (asset == null || asset.getSizeBytes() == null) {
            throw invalid("视觉素材不存在");
        }
        if (asset.getSizeBytes() > MAX_SIZE_BYTES) {
            throw invalid("视觉素材不能超过 500 KB");
        }
        byte[] bytes = fileStorage.read(asset.getStorageKey());
        if (bytes == null) {
            throw invalid("视觉素材不存在");
        }
        byte[] header = Arrays.copyOf(bytes, Math.min(bytes.length, HEADER_LIMIT));
        ImageHeader image = parseHeader(header);
        if (!image.alpha()) {
            throw invalid("视觉素材必须包含透明通道");
        }
        if (image.width() != width || image.height() != height) {
            throw invalid(label + "必须为 " + width + "×" + height + " 像素");
        }
    }

    private ImageHeader parseHeader(byte[] header) {
        if (isPng(header)) {
            return parsePng(header);
        }
        if (isWebp(header)) {
            return parseWebp(header);
        }
        throw invalid("视觉素材必须是带透明通道的 PNG 或 WebP 图片");
    }

    private boolean isPng(byte[] header) {
        return header.length >= PNG_SIGNATURE.length
                && Arrays.equals(
                        Arrays.copyOf(header, PNG_SIGNATURE.length),
                        PNG_SIGNATURE
                );
    }

    private ImageHeader parsePng(byte[] header) {
        if (header.length < 29
                || readInt(header, 8) != 13
                || header[12] != 'I'
                || header[13] != 'H'
                || header[14] != 'D'
                || header[15] != 'R') {
            throw invalid("PNG 文件头不完整");
        }
        int colorType = unsigned(header[25]);
        return new ImageHeader(
                readInt(header, 16),
                readInt(header, 20),
                colorType == 4 || colorType == 6
        );
    }

    private boolean isWebp(byte[] header) {
        return header.length >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P';
    }

    private ImageHeader parseWebp(byte[] header) {
        if (header.length < 21) {
            throw invalid("WebP 文件头不完整");
        }
        String chunk = new String(header, 12, 4);
        if ("VP8X".equals(chunk)) {
            if (header.length < 30) {
                throw invalid("WebP 文件头不完整");
            }
            return new ImageHeader(
                    read24LittleEndian(header, 24) + 1,
                    read24LittleEndian(header, 27) + 1,
                    (unsigned(header[20]) & 0x10) != 0
            );
        }
        if ("VP8L".equals(chunk)) {
            if (header.length < 25 || unsigned(header[20]) != 0x2f) {
                throw invalid("WebP 文件头不完整");
            }
            int b1 = unsigned(header[21]);
            int b2 = unsigned(header[22]);
            int b3 = unsigned(header[23]);
            int b4 = unsigned(header[24]);
            return new ImageHeader(
                    1 + b1 + ((b2 & 0x3f) << 8),
                    1 + ((b2 & 0xc0) >> 6) + (b3 << 2)
                            + ((b4 & 0x0f) << 10),
                    (b4 & 0x10) != 0
            );
        }
        throw invalid("WebP 必须使用扩展或无损透明编码");
    }

    private int readInt(byte[] bytes, int offset) {
        return (unsigned(bytes[offset]) << 24)
                | (unsigned(bytes[offset + 1]) << 16)
                | (unsigned(bytes[offset + 2]) << 8)
                | unsigned(bytes[offset + 3]);
    }

    private int read24LittleEndian(byte[] bytes, int offset) {
        return unsigned(bytes[offset])
                | (unsigned(bytes[offset + 1]) << 8)
                | (unsigned(bytes[offset + 2]) << 16);
    }

    private int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.COMMON_BAD_REQUEST, message);
    }

    private record ImageHeader(int width, int height, boolean alpha) {
    }
}
