package com.diybracelet.mall.diy.api;

import com.diybracelet.mall.auth.infrastructure.JwtAuthenticationFilter;
import com.diybracelet.mall.auth.infrastructure.JwtService;
import com.diybracelet.mall.auth.infrastructure.SecurityConfiguration;
import com.diybracelet.mall.diy.api.dto.DiySchemeResponse;
import com.diybracelet.mall.diy.application.DiySchemeService;
import com.diybracelet.mall.diy.domain.FitType;
import com.diybracelet.mall.diy.domain.QuoteStatus;
import com.diybracelet.mall.diy.domain.SizeStatus;
import com.diybracelet.mall.file.api.UploadedFileResponse;
import com.diybracelet.mall.file.application.FileStorageService;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtAuthenticationFilter;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtService;
import com.diybracelet.mall.shared.error.GlobalExceptionHandler;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 小程序 DIY 方案预览图上传 HTTP 契约测试。
 */
@WebMvcTest(AppDiySchemeController.class)
@Import({
        GlobalExceptionHandler.class,
        JwtService.class,
        JwtAuthenticationFilter.class,
        CustomerJwtService.class,
        CustomerJwtAuthenticationFilter.class,
        SecurityConfiguration.class
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
class AppDiyPreviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerJwtService customerJwtService;

    @MockBean
    private DiySchemeService schemeService;

    @MockBean
    private FileStorageService fileStorageService;

    @Test
    void shouldPassVersionZeroToTheGuardedPreviewBinding() throws Exception {
        when(fileStorageService.store(any())).thenReturn(new UploadedFileResponse(
                9001L, "preview.png", "preview.png", "image/png", 7, "/preview.png"
        ));
        mockMvc.perform(multipart("/api/app/diy/schemes/7001/previews/0")
                        .file(new MockMultipartFile("file", "preview.png", "image/png", new byte[]{1}))
                        .header("Authorization", "Bearer " + customerJwtService.issue(3001L)))
                .andExpect(status().isCreated());
        verify(schemeService).bindPreviewVersion(7001L, 9001L, 0L);
        verify(schemeService, never()).bindPreview(any(), any());
    }

    @Test
    void shouldRejectNegativePreviewVersionBeforeStoringFile() throws Exception {
        mockMvc.perform(multipart("/api/app/diy/schemes/7001/previews/-1")
                        .file(new MockMultipartFile("file", "preview.png", "image/png", new byte[]{1}))
                        .header("Authorization", "Bearer " + customerJwtService.issue(3001L)))
                .andExpect(status().isBadRequest());
        verify(fileStorageService, never()).store(any());
    }

    @Test
    void shouldExposeStalePreviewConflictSoClientCanDiscardTheJob() throws Exception {
        when(fileStorageService.store(any())).thenReturn(new UploadedFileResponse(
                9001L, "preview.png", "preview.png", "image/png", 7, "/preview.png"
        ));
        when(schemeService.bindPreviewVersion(7001L, 9001L, 0L)).thenThrow(
                new BusinessException(ErrorCode.DIY_SCHEME_VERSION_CONFLICT, "方案已更新")
        );
        mockMvc.perform(multipart("/api/app/diy/schemes/7001/previews/0")
                        .file(new MockMultipartFile("file", "preview.png", "image/png", new byte[]{1}))
                        .header("Authorization", "Bearer " + customerJwtService.issue(3001L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DIY_SCHEME_VERSION_CONFLICT"));
    }

    @Test
    void shouldUploadPreviewAndBindItToOwnedScheme() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scheme-preview.png",
                "image/png",
                "preview".getBytes(StandardCharsets.UTF_8)
        );
        UploadedFileResponse uploaded = new UploadedFileResponse(
                9001L,
                "2026/07/preview.png",
                "scheme-preview.png",
                "image/png",
                7,
                "/api/public/files/2026/07/preview.png"
        );
        DiySchemeResponse response = new DiySchemeResponse(
                7001L,
                "我的第一串",
                new BigDecimal("160.00"),
                FitType.STANDARD,
                6001L,
                List.of(2001L, 2002L),
                2,
                Map.of(2001L, 1, 2002L, 1),
                List.of(),
                QuoteStatus.VALID,
                new BigDecimal("16.00"),
                new BigDecimal("170.00"),
                new BigDecimal("-154.00"),
                SizeStatus.TOO_SHORT,
                new BigDecimal("29.60"),
                new BigDecimal("10.00"),
                new BigDecimal("0.00"),
                new BigDecimal("39.60"),
                "fingerprint",
                9001L,
                "/api/public/files/2026/07/preview.png",
                1L,
                null,
                null
        );

        when(fileStorageService.store(any())).thenReturn(uploaded);
        when(schemeService.bindPreview(7001L, 9001L)).thenReturn(response);

        mockMvc.perform(multipart("/api/app/diy/schemes/7001/preview")
                        .file(file)
                        .header(
                                "Authorization",
                                "Bearer " + customerJwtService.issue(3001L)
                        ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.previewAssetId").value(9001))
                .andExpect(jsonPath("$.data.previewUrl")
                        .value("/api/public/files/2026/07/preview.png"));
    }
}
