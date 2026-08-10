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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void shouldAddAvailableInventoryAndWriteAdjustmentLedger()
            throws Exception {
        String token = loginAsAdmin();
        InventoryBalance before = inventoryBalanceMapper.selectOne(
                new LambdaQueryWrapper<InventoryBalance>()
                        .eq(InventoryBalance::getBeadSkuId, 2001L)
        );

        mockMvc.perform(post("/api/admin/catalog/beads/2001/inventory")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 100,
                                  "note": "集成测试入库"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.beadSkuId").value(2001))
                .andExpect(jsonPath("$.data.addedQuantity").value(100))
                .andExpect(jsonPath("$.data.availableQty")
                        .value(before.getAvailableQty() + 100))
                .andExpect(jsonPath("$.data.reservedQty")
                        .value(before.getReservedQty()))
                .andExpect(jsonPath("$.data.soldQty")
                        .value(before.getSoldQty()));

        InventoryBalance after = inventoryBalanceMapper.selectOne(
                new LambdaQueryWrapper<InventoryBalance>()
                        .eq(InventoryBalance::getBeadSkuId, 2001L)
        );
        InventoryLedger ledger = inventoryLedgerMapper.selectOne(
                new LambdaQueryWrapper<InventoryLedger>()
                        .eq(InventoryLedger::getBeadSkuId, 2001L)
                        .eq(InventoryLedger::getChangeType, "ADJUST")
                        .eq(InventoryLedger::getNote, "集成测试入库")
        );

        assertThat(after.getAvailableQty())
                .isEqualTo(before.getAvailableQty() + 100);
        assertThat(after.getReservedQty()).isEqualTo(before.getReservedQty());
        assertThat(after.getSoldQty()).isEqualTo(before.getSoldQty());
        assertThat(ledger).isNotNull();
        assertThat(ledger.getQuantity()).isEqualTo(100);
        assertThat(ledger.getBeforeAvailableQty())
                .isEqualTo(before.getAvailableQty());
        assertThat(ledger.getAfterAvailableQty())
                .isEqualTo(before.getAvailableQty() + 100);
    }

    @Test
    void shouldRejectNonPositiveInventoryAdjustment() throws Exception {
        String token = loginAsAdmin();
        InventoryBalance before = inventoryBalanceMapper.selectOne(
                new LambdaQueryWrapper<InventoryBalance>()
                        .eq(InventoryBalance::getBeadSkuId, 2001L)
        );

        mockMvc.perform(post("/api/admin/catalog/beads/2001/inventory")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"));

        InventoryBalance after = inventoryBalanceMapper.selectOne(
                new LambdaQueryWrapper<InventoryBalance>()
                        .eq(InventoryBalance::getBeadSkuId, 2001L)
        );
        assertThat(after.getAvailableQty()).isEqualTo(before.getAvailableQty());
    }

    @Test
    void shouldFilterBeadsAndReturnTheUploadedCoverUrl() throws Exception {
        String token = loginAsAdmin();
        jdbcTemplate.update(
                """
                insert into asset_file (
                    storage_key, original_name, content_type,
                    size_bytes, public_url, status
                ) values (?, ?, ?, ?, ?, 'ACTIVE')
                """,
                "tests/admin-sandalwood.png",
                "admin-sandalwood.png",
                "image/png",
                128L,
                "/api/public/files/tests/admin-sandalwood.png"
        );
        Long assetId = jdbcTemplate.queryForObject(
                "select id from asset_file where storage_key = ?",
                Long.class,
                "tests/admin-sandalwood.png"
        );
        jdbcTemplate.update(
                "update bead_sku set cover_asset_id = ? where id = 2004",
                assetId
        );

        mockMvc.perform(get("/api/admin/catalog/beads")
                        .header("Authorization", "Bearer " + token)
                        .param("categoryId", "1002")
                        .param("attribute", "檀木 棕色 8mm")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(2004))
                .andExpect(jsonPath("$.data.items[0].coverAssetId")
                        .value(assetId))
                .andExpect(jsonPath("$.data.items[0].coverUrl")
                        .value("/api/public/files/tests/admin-sandalwood.png"))
                .andExpect(jsonPath("$.data.items[0].productType")
                        .value("BEAD"))
                .andExpect(jsonPath("$.data.items[0].availableQty")
                        .value(200))
                .andExpect(jsonPath("$.data.items[0].reservedQty")
                        .value(0))
                .andExpect(jsonPath("$.data.items[0].stockAlertThreshold")
                        .value(15))
                .andExpect(jsonPath("$.data.items[0].stockWarningStatus")
                        .value("NORMAL"));
    }

    @Test
    void shouldMarkZeroAvailableStockForRestocking() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/admin/catalog/beads")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "BEAD-CRYSTAL-001")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].availableQty")
                        .value(200))
                .andExpect(jsonPath("$.data.items[0].stockWarningStatus")
                        .value("NORMAL"));

        InventoryBalance balance = inventoryBalanceMapper.selectOne(
                new LambdaQueryWrapper<InventoryBalance>()
                        .eq(InventoryBalance::getBeadSkuId, 2001L)
        );
        balance.setAvailableQty(20);
        inventoryBalanceMapper.updateById(balance);

        mockMvc.perform(get("/api/admin/catalog/beads")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "BEAD-CRYSTAL-001")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].availableQty")
                        .value(20))
                .andExpect(jsonPath("$.data.items[0].stockWarningStatus")
                        .value("RESTOCK"));
    }

    @Test
    void shouldCreateCordWithoutBeadDiameter() throws Exception {
        String token = loginAsAdmin();
        Long cordCategoryId = jdbcTemplate.queryForObject(
                "select id from category where slug = 'cord'",
                Long.class
        );

        mockMvc.perform(post("/api/admin/catalog/beads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": %d,
                                  "skuCode": "CORD-ELASTIC-TEST-001",
                                  "name": "透明弹力绳",
                                  "material": "弹力线",
                                  "color": "透明",
                                  "salePrice": 9.00,
                                  "costPrice": 2.00,
                                  "stockAlertThreshold": 10,
                                  "initialStock": 200,
                                  "tags": ["弹力绳"],
                                  "status": "ON_SALE"
                                }
                                """.formatted(cordCategoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.skuCode")
                        .value("CORD-ELASTIC-TEST-001"));

        mockMvc.perform(get("/api/admin/catalog/beads")
                        .header("Authorization", "Bearer " + token)
                        .param("categoryId", String.valueOf(cordCategoryId))
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productType")
                        .value("CORD"))
                .andExpect(jsonPath("$.data.items[0].availableQty")
                        .value(200))
                .andExpect(jsonPath("$.data.items[0].stockWarningStatus")
                        .value("NORMAL"));

        mockMvc.perform(get("/api/public/catalog/beads")
                        .param("categoryId", String.valueOf(cordCategoryId))
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        BigDecimal diameter = jdbcTemplate.queryForObject(
                "select diameter_mm from bead_sku where sku_code = 'CORD-ELASTIC-TEST-001'",
                BigDecimal.class
        );
        assertThat(diameter).isNull();
    }

    @Test
    void shouldRejectBeadWithoutDiameter() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(post("/api/admin/catalog/beads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 1001,
                                  "skuCode": "BEAD-MISSING-DIAMETER",
                                  "name": "缺少珠径的珠子",
                                  "material": "水晶",
                                  "color": "透明",
                                  "salePrice": 9.00,
                                  "costPrice": 2.00,
                                  "stockAlertThreshold": 10,
                                  "initialStock": 200,
                                  "tags": [],
                                  "status": "DRAFT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("珠子商品必须填写珠径"));
    }

    @Test
    void shouldDownloadCurrentExcelImportTemplate() throws Exception {
        String token = loginAsAdmin();

        byte[] content = mockMvc.perform(get(
                            "/api/admin/catalog/beads/import-template"
                        )
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(
                        result.getResponse().getContentType()
                ).isEqualTo(
                        "application/vnd.openxmlformats-officedocument"
                                + ".spreadsheetml.sheet"
                ))
                .andExpect(result -> assertThat(
                        result.getResponse().getHeader("Content-Disposition")
                ).contains("attachment"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(content).startsWith((byte) 'P', (byte) 'K');
        try (Workbook workbook = new XSSFWorkbook(
                new java.io.ByteArrayInputStream(content)
        )) {
            assertThat(workbook.getSheet("商品数据")).isNotNull();
            assertThat(workbook.getSheet("填写说明")).isNotNull();
            assertThat(workbook.getSheet("商品数据")
                    .getRow(0)
                    .getCell(0)
                    .getStringCellValue()).isEqualTo("SKU编码");
        }
    }

    @Test
    void shouldImportValidRowsAndReportInvalidRowsWithoutImages()
            throws Exception {
        String token = loginAsAdmin();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "catalog.xlsx",
                "application/vnd.openxmlformats-officedocument"
                        + ".spreadsheetml.sheet",
                createImportWorkbook()
        );

        mockMvc.perform(multipart("/api/admin/catalog/beads/import")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.errors[0].rowNumber").value(4))
                .andExpect(jsonPath("$.data.errors[0].skuCode")
                        .value("BEAD-EXCEL-MISSING-DIAMETER"))
                .andExpect(jsonPath("$.data.errors[0].message")
                        .value("珠子商品必须填写珠径"));

        BeadSku bead = beadSkuMapper.selectOne(
                new LambdaQueryWrapper<BeadSku>()
                        .eq(BeadSku::getSkuCode, "BEAD-EXCEL-001")
        );
        InventoryBalance beadBalance = inventoryBalanceMapper.selectOne(
                new LambdaQueryWrapper<InventoryBalance>()
                        .eq(InventoryBalance::getBeadSkuId, bead.getId())
        );
        BeadSku cord = beadSkuMapper.selectOne(
                new LambdaQueryWrapper<BeadSku>()
                        .eq(BeadSku::getSkuCode, "CORD-EXCEL-001")
        );

        assertThat(bead.getCoverAssetId()).isNull();
        assertThat(beadBalance.getAvailableQty()).isEqualTo(77);
        assertThat(cord.getDiameterMm()).isNull();
        assertThat(beadSkuMapper.selectCount(
                new LambdaQueryWrapper<BeadSku>()
                        .eq(
                                BeadSku::getSkuCode,
                                "BEAD-EXCEL-MISSING-DIAMETER"
                        )
        )).isZero();
    }

    /**
     * 套餐编辑页需要读取稳定排序的散珠明细，不能只返回套餐主表。
     */
    @Test
    void shouldReturnOrderedPackageItemsForAdministration() throws Exception {
        String token = loginAsAdmin();
        jdbcTemplate.update(
                """
                insert into asset_file (
                    storage_key, original_name, content_type,
                    size_bytes, public_url, status
                ) values (?, ?, ?, ?, ?, 'ACTIVE')
                """,
                "tests/package-detail-cover.webp",
                "package-detail-cover.webp",
                "image/webp",
                256L,
                "/api/public/files/tests/package-detail-cover.webp"
        );
        Long coverAssetId = jdbcTemplate.queryForObject(
                "select id from asset_file where storage_key = ?",
                Long.class,
                "tests/package-detail-cover.webp"
        );
        jdbcTemplate.update(
                "update product_package set cover_asset_id = ? where id = 3001",
                coverAssetId
        );

        mockMvc.perform(get("/api/admin/catalog/packages/3001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageCode")
                        .value("PACKAGE-CALM-001"))
                .andExpect(jsonPath("$.data.coverAssetId")
                        .value(coverAssetId))
                .andExpect(jsonPath("$.data.coverUrl")
                        .value("/api/public/files/tests/package-detail-cover.webp"))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].beadSkuId")
                        .value(2001))
                .andExpect(jsonPath("$.data.items[0].sortOrder")
                        .value(10));
    }

    @Test
    void shouldMarkPackageAsRecommendedForHome() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(patch(
                            "/api/admin/catalog/packages/3001/recommendation"
                        )
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recommended": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3001))
                .andExpect(jsonPath("$.data.recommended").value(true));

        Boolean recommended = jdbcTemplate.queryForObject(
                "select recommended from product_package where id = 3001",
                Boolean.class
        );
        assertThat(recommended).isTrue();
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

    private byte[] createImportWorkbook() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("商品数据");
            writeRow(sheet.createRow(0), new String[]{
                    "SKU编码", "商品名称", "分类名称", "材质", "颜色",
                    "珠径(mm)", "销售价(元)", "成本价(元)", "初始库存",
                    "库存预警", "标签", "状态"
            });
            writeRow(sheet.createRow(1), new String[]{
                    "BEAD-EXCEL-001", "Excel 白水晶", "天然水晶", "水晶",
                    "透明", "8", "9.80", "2.10", "77", "10",
                    "日常，通勤", "已上架"
            });
            writeRow(sheet.createRow(2), new String[]{
                    "CORD-EXCEL-001", "Excel 弹力绳", "绳类", "弹力线",
                    "透明", "", "8.00", "1.50", "66", "8",
                    "弹力绳", "草稿"
            });
            writeRow(sheet.createRow(3), new String[]{
                    "BEAD-EXCEL-MISSING-DIAMETER", "缺珠径珠子", "天然水晶",
                    "水晶", "紫色", "", "10", "2", "50", "5", "",
                    "草稿"
            });
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void writeRow(Row row, String[] values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(index).setCellValue(values[index]);
        }
    }
}
