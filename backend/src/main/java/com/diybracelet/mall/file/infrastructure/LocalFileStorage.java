package com.diybracelet.mall.file.infrastructure;

import com.diybracelet.mall.file.domain.FileStorage;
import com.diybracelet.mall.file.domain.StoredFile;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 将目录图片存储在项目本地磁盘的 {@link FileStorage} 适配器。
 *
 * <p>原始文件名永远不会参与磁盘路径生成，真正的存储键由日期目录、UUID 和
 * MIME 类型对应的安全扩展名组成。所有目标路径在写入前都会规范化并检查仍位于
 * 配置根目录内，从源头阻止目录穿越。</p>
 */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/webp", ".webp"
    );
    private static final DateTimeFormatter YEAR_MONTH_PATH =
            DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path root;
    private final long maxSizeBytes;

    /**
     * Spring 运行时构造器。
     *
     * @param rootDirectory 本地文件根目录
     * @param maxSizeBytes 单个文件最大字节数
     */
    @Autowired
    public LocalFileStorage(
            @Value("${app.file-storage.local.root:storage/catalog}")
            String rootDirectory,
            @Value("${app.file-storage.max-size-bytes:5000000}")
            long maxSizeBytes
    ) {
        this(Path.of(rootDirectory), maxSizeBytes);
    }

    /**
     * 便于测试或非 Spring 场景显式传入目录。
     *
     * @param root 本地文件根目录
     * @param maxSizeBytes 单个文件最大字节数，必须大于零
     */
    public LocalFileStorage(Path root, long maxSizeBytes) {
        this.root = Objects.requireNonNull(root, "root must not be null")
                .toAbsolutePath()
                .normalize();
        if (maxSizeBytes <= 0) {
            throw new IllegalArgumentException(
                    "maxSizeBytes must be greater than zero"
            );
        }
        this.maxSizeBytes = maxSizeBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StoredFile store(
            String originalName,
            String contentType,
            byte[] content
    ) {
        validateOriginalName(originalName);
        String normalizedContentType = normalizeContentType(contentType);
        byte[] safeContent = Objects.requireNonNull(
                content,
                "content must not be null"
        );
        if (safeContent.length > maxSizeBytes) {
            throw new IllegalArgumentException(
                    "文件大小不能超过 " + maxSizeBytes + " 字节"
            );
        }

        String extension = ALLOWED_CONTENT_TYPES.get(normalizedContentType);
        String storageKey = YEAR_MONTH_PATH.format(LocalDate.now())
                + "/"
                + UUID.randomUUID()
                + extension;
        Path target = resolveInsideRoot(storageKey);

        try {
            Files.createDirectories(target.getParent());
            rejectSymbolicLinks(target);
            Files.write(target, safeContent, StandardOpenOption.CREATE_NEW,
                    LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new UncheckedIOException("写入本地文件失败", exception);
        }

        return new StoredFile(
                storageKey,
                originalName,
                normalizedContentType,
                safeContent.length,
                "/api/public/files/" + storageKey
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] read(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "文件存储键不能为空"
            );
        }
        Path target;
        try {
            target = resolveInsideRoot(storageKey);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "文件存储键不合法"
            );
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "文件不存在"
            );
        }
        try {
            try (var input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
                return input.readAllBytes();
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("读取本地文件失败", exception);
        }
    }

    private void validateOriginalName(String originalName) {
        if (originalName == null
                || originalName.isBlank()
                || originalName.contains("/")
                || originalName.contains("\\")
                || originalName.contains("..")) {
            throw new IllegalArgumentException("原始文件名不合法");
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            throw new IllegalArgumentException("文件类型不能为空");
        }
        String normalized = contentType
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.containsKey(normalized)) {
            throw new IllegalArgumentException(
                    "仅支持 PNG、JPEG 和 WebP 图片"
            );
        }
        return normalized;
    }

    private Path resolveInsideRoot(String storageKey) {
        Path relativePath = Path.of(storageKey);
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("不允许使用绝对存储路径");
        }
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("存储路径不能超出配置根目录");
        }
        rejectSymbolicLinks(target);
        return target;
    }

    private void rejectSymbolicLinks(Path target) {
        Path current = root;
        for (Path segment : root.relativize(target)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("存储路径不能包含符号链接");
            }
        }
    }
}
