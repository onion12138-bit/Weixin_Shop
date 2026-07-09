package com.diybracelet.mall.catalog.api;

import com.diybracelet.mall.auth.infrastructure.JwtAuthenticationFilter;
import com.diybracelet.mall.auth.infrastructure.JwtService;
import com.diybracelet.mall.auth.infrastructure.SecurityConfiguration;
import com.diybracelet.mall.catalog.api.dto.BeadSkuSummary;
import com.diybracelet.mall.catalog.api.dto.PageResponse;
import com.diybracelet.mall.catalog.application.CatalogQueryService;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtAuthenticationFilter;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtService;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import com.diybracelet.mall.shared.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 不依赖数据库的公开目录 HTTP 契约测试。
 */
@WebMvcTest(PublicCatalogController.class)
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
class PublicCatalogControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogQueryService catalogQueryService;

    @Test
    void shouldReturnStableBeadPage() throws Exception {
        BeadSkuSummary bead = new BeadSkuSummary(
                2001L,
                1001L,
                "BEAD-CRYSTAL-001",
                "白水晶 8mm",
                "天然水晶",
                "透明",
                new BigDecimal("8.00"),
                new BigDecimal("12.80"),
                null,
                List.of("净化", "日常")
        );
        when(catalogQueryService.listBeads(
                eq(null),
                eq(null),
                eq(1),
                eq(20)
        )).thenReturn(new PageResponse<>(
                List.of(bead),
                1,
                1,
                20
        ));

        mockMvc.perform(get("/api/public/catalog/beads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items[0].skuCode")
                        .value("BEAD-CRYSTAL-001"))
                .andExpect(jsonPath("$.data.items[0].costPrice")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void shouldReturnStableNotFoundResponse() throws Exception {
        when(catalogQueryService.getBead(999999L)).thenThrow(
                new BusinessException(
                        ErrorCode.COMMON_NOT_FOUND,
                        "散珠不存在或已下架"
                )
        );

        mockMvc.perform(get("/api/public/catalog/beads/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
    }

    @Test
    void shouldRejectInvalidPageWithoutCallingService() throws Exception {
        when(catalogQueryService.listBeads(
                any(),
                any(),
                anyInt(),
                anyInt()
        )).thenThrow(new AssertionError("参数校验前不应调用查询服务"));

        mockMvc.perform(get("/api/public/catalog/beads")
                        .queryParam("page", "0")
                        .queryParam("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"));
    }
}
