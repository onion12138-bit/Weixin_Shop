package com.diybracelet.mall.file.api;

import com.diybracelet.mall.auth.infrastructure.JwtAuthenticationFilter;
import com.diybracelet.mall.auth.infrastructure.JwtService;
import com.diybracelet.mall.auth.infrastructure.SecurityConfiguration;
import com.diybracelet.mall.file.application.FileStorageService;
import com.diybracelet.mall.file.domain.AssetFile;
import com.diybracelet.mall.file.domain.FileStorage;
import com.diybracelet.mall.file.domain.StoredFile;
import com.diybracelet.mall.file.infrastructure.AssetFileMapper;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtAuthenticationFilter;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtService;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.diybracelet.mall.shared.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文件 HTTP 接口契约测试。
 *
 * <p>使用内存文件端口和 Mock 元数据 Mapper，不会访问真实磁盘、数据库或 Docker。</p>
 */
@WebMvcTest({
        AdminFileController.class,
        PublicFileController.class
})
@Import({
        GlobalExceptionHandler.class,
        FileStorageService.class,
        JwtService.class,
        JwtAuthenticationFilter.class,
        CustomerJwtService.class,
        CustomerJwtAuthenticationFilter.class,
        SecurityConfiguration.class,
        FileControllerTest.StorageTestConfiguration.class
})
@TestPropertySource(properties = {
        "app.auth.jwt-secret=01234567890123456789012345678901",
        "app.auth.jwt-ttl=PT8H",
        "app.auth.jwt-issuer=diy-bracelet-test",
        "app.customer-auth.jwt-secret=customer-test-secret-0123456789012345",
        "app.customer-auth.jwt-ttl=P30D",
        "app.customer-auth.jwt-issuer=diy-customer-test",
        "app.customer-auth.identity-pepper=identity-test-pepper-0123456789012"
})
class FileControllerTest {

    private static final String STORAGE_KEY =
            "2026/07/00000000-0000-0000-0000-000000000001.png";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryFileStorage fileStorage;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AssetFileMapper assetFileMapper;

    @BeforeEach
    void clearStoredFiles() {
        fileStorage.clear();
        doAnswer(invocation -> {
            AssetFile assetFile = invocation.getArgument(0);
            assetFile.setId(501L);
            return 1;
        }).when(assetFileMapper).insert(any(AssetFile.class));
    }

    @Test
    void shouldUploadCatalogImage() throws Exception {
        byte[] bytes = "png-data".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "bracelet.png",
                "image/png",
                bytes
        );

        mockMvc.perform(multipart("/api/admin/files")
                        .file(multipartFile)
                        .header(
                                "Authorization",
                                "Bearer "
                                        + jwtService.issueAccessToken("admin")
                        ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(501))
                .andExpect(jsonPath("$.data.storageKey").value(STORAGE_KEY))
                .andExpect(jsonPath("$.data.contentType").value("image/png"));
    }

    @Test
    void shouldReadStorageKeyContainingDatePath() throws Exception {
        byte[] bytes = "png-data".getBytes(StandardCharsets.UTF_8);
        fileStorage.put(STORAGE_KEY, bytes);

        mockMvc.perform(get("/api/public/files/" + STORAGE_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void shouldReturnStableNotFoundResponse() throws Exception {
        mockMvc.perform(get("/api/public/files/" + STORAGE_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("文件不存在"));
    }

    /**
     * 为 MVC 切片提供无外部依赖的文件存储测试替身。
     */
    @TestConfiguration
    static class StorageTestConfiguration {

        @Bean
        InMemoryFileStorage fileStorage() {
            return new InMemoryFileStorage();
        }
    }

    /**
     * 仅保存在测试进程内存中的端口实现。
     */
    static class InMemoryFileStorage implements FileStorage {

        private final Map<String, byte[]> files = new HashMap<>();

        @Override
        public StoredFile store(
                String originalName,
                String contentType,
                byte[] content
        ) {
            files.put(STORAGE_KEY, content.clone());
            return new StoredFile(
                    STORAGE_KEY,
                    originalName,
                    contentType,
                    content.length,
                    "/api/public/files/" + STORAGE_KEY
            );
        }

        @Override
        public byte[] read(String storageKey) {
            byte[] content = files.get(storageKey);
            if (content == null) {
                throw new BusinessException(
                        ErrorCode.COMMON_NOT_FOUND,
                        "文件不存在"
                );
            }
            return content.clone();
        }

        void put(String storageKey, byte[] content) {
            files.put(storageKey, content.clone());
        }

        void clear() {
            files.clear();
        }
    }
}
