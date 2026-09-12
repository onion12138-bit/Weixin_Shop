package com.diybracelet.mall.file.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地文件存储的边界与路径安全测试。
 *
 * <p>测试使用 JUnit 临时目录，不依赖项目真实 {@code storage} 目录，因此不会留下测试文件。
 * 除了成功写入，还验证恶意文件名、不允许的媒体类型和超限内容均在落盘前被拒绝。</p>
 */
class LocalFileStorageTest {

    private static final long FIVE_MEGABYTES = 5_000_000L;

    @TempDir
    Path root;

    @Test
    void shouldStoreInsideConfiguredRoot() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root, FIVE_MEGABYTES);
        byte[] content = "image-data".getBytes(StandardCharsets.UTF_8);

        var stored = storage.store("bracelet.png", "image/png", content);
        Path storedPath = root.resolve(stored.storageKey()).normalize();

        assertThat(storedPath).startsWith(root.toAbsolutePath().normalize());
        assertThat(Files.readAllBytes(storedPath)).isEqualTo(content);
        assertThat(stored.originalName()).isEqualTo("bracelet.png");
        assertThat(stored.publicUrl())
                .isEqualTo("/api/public/files/" + stored.storageKey());
    }

    @Test
    void shouldRejectExecutableAndTraversalName() {
        LocalFileStorage storage = new LocalFileStorage(root, FIVE_MEGABYTES);

        assertThatThrownBy(() ->
                storage.store("../attack.sh", "text/x-shellscript", new byte[0])
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                storage.store("attack.png", "application/x-sh", new byte[0])
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectContentLargerThanConfiguredLimit() {
        LocalFileStorage storage = new LocalFileStorage(root, 4);

        assertThatThrownBy(() ->
                storage.store("large.webp", "image/webp", new byte[5])
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4");
    }

    @Test
    void shouldUseTheDecimalFiveMegabyteBoundary() {
        LocalFileStorage storage = new LocalFileStorage(root, FIVE_MEGABYTES);

        assertThatThrownBy(() -> storage.store(
                "large.webp",
                "image/webp",
                new byte[5_000_001]
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5000000");
    }

    @Test
    void shouldAcceptExactlyFiveDecimalMegabytes() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root, FIVE_MEGABYTES);

        var stored = storage.store(
                "boundary.webp",
                "image/webp",
                new byte[5_000_000]
        );

        assertThat(Files.size(root.resolve(stored.storageKey())))
                .isEqualTo(FIVE_MEGABYTES);
    }

    @Test
    void shouldRejectReadingFileThroughSymlinkOutsideStorage() throws Exception {
        Path storageRoot = Files.createDirectory(root.resolve("storage"));
        Path privateFile = Files.writeString(root.resolve("secret.txt"), "private");
        Files.createSymbolicLink(storageRoot.resolve("public.png"), privateFile);
        LocalFileStorage storage = new LocalFileStorage(storageRoot, FIVE_MEGABYTES);

        assertThatThrownBy(() -> storage.read("public.png"))
                .isInstanceOf(com.diybracelet.mall.shared.error.BusinessException.class);
    }

    @Test
    void shouldRejectReadingThroughSymlinkDirectory() throws Exception {
        Path storageRoot = Files.createDirectory(root.resolve("storage"));
        Path privateRoot = Files.createDirectory(root.resolve("private"));
        Files.writeString(privateRoot.resolve("secret.png"), "private");
        Files.createSymbolicLink(storageRoot.resolve("linked"), privateRoot);
        LocalFileStorage storage = new LocalFileStorage(storageRoot, FIVE_MEGABYTES);

        assertThatThrownBy(() -> storage.read("linked/secret.png"))
                .isInstanceOf(com.diybracelet.mall.shared.error.BusinessException.class);
    }

    @Test
    void shouldRejectWritingThroughSymlinkDirectory() throws Exception {
        Path storageRoot = Files.createDirectory(root.resolve("storage"));
        Path outside = Files.createDirectory(root.resolve("outside"));
        String year = String.valueOf(java.time.LocalDate.now().getYear());
        Files.createSymbolicLink(storageRoot.resolve(year), outside);
        LocalFileStorage storage = new LocalFileStorage(storageRoot, FIVE_MEGABYTES);

        assertThatThrownBy(() -> storage.store("photo.png", "image/png", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        try (var files = Files.list(outside)) {
            assertThat(files).isEmpty();
        }
    }
}
