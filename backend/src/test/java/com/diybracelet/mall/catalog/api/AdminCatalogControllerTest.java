package com.diybracelet.mall.catalog.api;

import com.diybracelet.mall.support.MySqlIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后台商品维护接口的真实 MySQL 集成测试。
 */
@Transactional
@AutoConfigureMockMvc
@Import(AdminCatalogController.class)
class AdminCatalogControllerTest extends MySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateBeadWithAdminToken() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(post("/api/admin/catalog/beads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
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
                                  "tags": ["净化", "日常"],
                                  "status": "ON_SALE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.skuCode")
                        .value("BEAD-TEST-001"))
                .andExpect(jsonPath("$.data.costPrice").value(5.20));
    }

    /**
     * 套餐编辑页需要读取稳定排序的散珠明细，不能只返回套餐主表。
     */
    @Test
    void shouldReturnOrderedPackageItemsForAdministration() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/admin/catalog/packages/3001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageCode")
                        .value("PACKAGE-CALM-001"))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].beadSkuId")
                        .value(2001))
                .andExpect(jsonPath("$.data.items[0].sortOrder")
                        .value(10));
    }

    private String loginAsAdmin() throws Exception {
        String responseBody = mockMvc.perform(
                        post("/api/admin/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "admin",
                                          "password": "Admin@123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);
        return response
                .path("data")
                .path("accessToken")
                .asText();
    }
}
