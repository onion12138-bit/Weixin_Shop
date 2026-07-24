package com.diybracelet.mall.catalog.api;

import com.diybracelet.mall.auth.infrastructure.JwtAuthenticationFilter;
import com.diybracelet.mall.auth.infrastructure.JwtService;
import com.diybracelet.mall.auth.infrastructure.SecurityConfiguration;
import com.diybracelet.mall.catalog.application.AdminCatalogService;
import com.diybracelet.mall.catalog.application.CatalogExcelImportService;
import com.diybracelet.mall.catalog.domain.BeadSku;
import com.diybracelet.mall.catalog.domain.CatalogStatus;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtAuthenticationFilter;
import com.diybracelet.mall.identity.infrastructure.CustomerJwtService;
import com.diybracelet.mall.shared.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后台商品控制器与 JWT 安全链的无数据库契约测试。
 */
@WebMvcTest(AdminCatalogController.class)
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
class AdminCatalogControllerWebTest {

    private static final String CREATE_BEAD_JSON = """
            {
              "categoryId": 1001,
              "skuCode": "BEAD-TEST-001",
              "name": "测试白水晶",
              "material": "水晶",
              "color": "透明",
              "diameterMm": 8.00,
              "salePrice": 12.80,
              "costPrice": 5.20,
              "stockAlertThreshold": 10,
              "initialStock": 200,
              "tags": ["净化", "日常"],
              "status": "ON_SALE"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AdminCatalogService adminCatalogService;

    @MockBean
    private CatalogExcelImportService catalogExcelImportService;

    @Test
    void shouldCreateBeadForAuthenticatedAdmin() throws Exception {
        BeadSku bead = new BeadSku();
        bead.setId(9001L);
        bead.setCategoryId(1001L);
        bead.setSkuCode("BEAD-TEST-001");
        bead.setName("测试白水晶");
        bead.setMaterial("水晶");
        bead.setColor("透明");
        bead.setDiameterMm(new BigDecimal("8.00"));
        bead.setSalePrice(new BigDecimal("12.80"));
        bead.setCostPrice(new BigDecimal("5.20"));
        bead.setStockAlertThreshold(10);
        bead.setTagsJson("[\"净化\",\"日常\"]");
        bead.setStatus(CatalogStatus.ON_SALE);
        when(adminCatalogService.createBead(any())).thenReturn(bead);

        mockMvc.perform(post("/api/admin/catalog/beads")
                        .header(
                                "Authorization",
                                "Bearer "
                                        + jwtService.issueAccessToken("admin")
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BEAD_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(9001))
                .andExpect(jsonPath("$.data.skuCode")
                        .value("BEAD-TEST-001"))
                .andExpect(jsonPath("$.data.costPrice").value(5.20));
    }

    @Test
    void shouldRejectAnonymousCreate() throws Exception {
        mockMvc.perform(post("/api/admin/catalog/beads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BEAD_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_UNAUTHORIZED"));
    }
}
