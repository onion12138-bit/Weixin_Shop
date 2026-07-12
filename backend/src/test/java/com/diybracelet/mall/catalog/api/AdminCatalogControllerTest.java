package com.diybracelet.mall.catalog.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.diybracelet.mall.catalog.domain.BeadSku;
import com.diybracelet.mall.catalog.infrastructure.mapper.BeadSkuMapper;
import com.diybracelet.mall.inventory.domain.InventoryBalance;
import com.diybracelet.mall.inventory.domain.InventoryLedger;
import com.diybracelet.mall.inventory.infrastructure.mapper.InventoryBalanceMapper;
import com.diybracelet.mall.inventory.infrastructure.mapper.InventoryLedgerMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
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

    @Autowired
    private BeadSkuMapper beadSkuMapper;

    @Autowired
    private InventoryBalanceMapper inventoryBalanceMapper;

    @Autowired
    private InventoryLedgerMapper inventoryLedgerMapper;

    @Test
    void shouldCreateBeadAndInitialInventoryAtomically() throws Exception {
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
                                  "initialStock": 200,
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

        BeadSku bead = beadSkuMapper.selectOne(
                new LambdaQueryWrapper<BeadSku>()
                        .eq(BeadSku::getSkuCode, "BEAD-TEST-001")
        );
        InventoryBalance balance = inventoryBalanceMapper.selectOne(
                new LambdaQueryWrapper<InventoryBalance>()
                        .eq(InventoryBalance::getBeadSkuId, bead.getId())
        );
        InventoryLedger ledger = inventoryLedgerMapper.selectOne(
                new LambdaQueryWrapper<InventoryLedger>()
                        .eq(InventoryLedger::getBeadSkuId, bead.getId())
                        .eq(InventoryLedger::getChangeType, "INIT")
        );

        assertThat(balance.getAvailableQty()).isEqualTo(200);
        assertThat(balance.getReservedQty()).isZero();
        assertThat(balance.getSoldQty()).isZero();
        assertThat(ledger.getChangeType()).isEqualTo("INIT");
        assertThat(ledger.getAfterAvailableQty()).isEqualTo(200);
    }

    @Test
    void shouldRejectNegativeInitialStock() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(post("/api/admin/catalog/beads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 1001,
                                  "skuCode": "BEAD-TEST-NEGATIVE",
                                  "name": "负库存测试珠",
                                  "material": "水晶",
                                  "color": "透明",
                                  "diameterMm": 8.00,
                                  "salePrice": 12.80,
                                  "costPrice": 5.20,
                                  "stockAlertThreshold": 10,
                                  "initialStock": -1,
                                  "tags": [],
                                  "status": "DRAFT"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(beadSkuMapper.selectCount(
                new LambdaQueryWrapper<BeadSku>()
                        .eq(BeadSku::getSkuCode, "BEAD-TEST-NEGATIVE")
        )).isZero();
    }

    @Test
    void shouldCreateZeroStockBalanceWithoutInitLedger() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(post("/api/admin/catalog/beads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 1001,
                                  "skuCode": "BEAD-TEST-ZERO",
                                  "name": "零库存测试珠",
                                  "material": "水晶",
                                  "color": "透明",
                                  "diameterMm": 8.00,
                                  "salePrice": 12.80,
                                  "costPrice": 5.20,
                                  "stockAlertThreshold": 10,
                                  "initialStock": 0,
                                  "tags": [],
                                  "status": "DRAFT"
                                }
                                """))
                .andExpect(status().isCreated());

        BeadSku bead = beadSkuMapper.selectOne(
                new LambdaQueryWrapper<BeadSku>()
                        .eq(BeadSku::getSkuCode, "BEAD-TEST-ZERO")
        );
        InventoryBalance balance = inventoryBalanceMapper.selectOne(
                new LambdaQueryWrapper<InventoryBalance>()
                        .eq(InventoryBalance::getBeadSkuId, bead.getId())
        );
        Long initLedgerCount = inventoryLedgerMapper.selectCount(
                new LambdaQueryWrapper<InventoryLedger>()
                        .eq(InventoryLedger::getBeadSkuId, bead.getId())
                        .eq(InventoryLedger::getChangeType, "INIT")
        );

        assertThat(balance.getAvailableQty()).isZero();
        assertThat(initLedgerCount).isZero();
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
