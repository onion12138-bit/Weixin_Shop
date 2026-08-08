package com.diybracelet.mall.catalog.api;

import com.diybracelet.mall.catalog.domain.CatalogStatus;
import com.diybracelet.mall.catalog.domain.ProductPackage;
import com.diybracelet.mall.catalog.infrastructure.mapper.ProductPackageMapper;
import com.diybracelet.mall.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开目录 API 的真实 MySQL 集成测试。
 *
 * <p>测试通过 Flyway 演示数据验证响应契约、公开可见性和参数边界，
 * 同时确保安全链允许小程序匿名访问。</p>
 */
@AutoConfigureMockMvc
@Import(PublicCatalogController.class)
class PublicCatalogControllerTest extends MySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductPackageMapper productPackageMapper;

    @Test
    void shouldListVisibleBeadsWithStablePageContract() throws Exception {
        mockMvc.perform(get("/api/public/catalog/beads")
                        .queryParam("page", "1")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items[0].skuCode").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].costPrice").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].diyVisual").isEmpty())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void shouldReturnBusinessNotFoundForMissingBead() throws Exception {
        mockMvc.perform(get("/api/public/catalog/beads/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
    }

    @Test
    void shouldRejectPageOutsidePublicContract() throws Exception {
        mockMvc.perform(get("/api/public/catalog/beads")
                        .queryParam("page", "0")
                        .queryParam("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"));
    }

    @Test
    @Transactional
    void shouldListOnlyRecommendedOnSalePackagesForHome() throws Exception {
        ProductPackage productPackage = productPackageMapper.selectById(3001L);
        productPackage.setRecommended(true);
        productPackageMapper.updateById(productPackage);

        mockMvc.perform(get("/api/public/catalog/packages/recommended")
                        .queryParam("limit", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(3001))
                .andExpect(jsonPath("$.data[0].name")
                        .value("澄心日常手串"));

        productPackage.setStatus(CatalogStatus.OFF_SALE);
        productPackageMapper.updateById(productPackage);

        mockMvc.perform(get("/api/public/catalog/packages/recommended")
                        .queryParam("limit", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
