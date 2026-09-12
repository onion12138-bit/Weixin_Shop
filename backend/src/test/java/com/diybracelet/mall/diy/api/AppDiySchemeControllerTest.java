package com.diybracelet.mall.diy.api;

import com.diybracelet.mall.support.MySqlIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 小程序 DIY 方案生命周期接口集成测试。
 *
 * <p>方案只保存 SKU 顺序数组和报价快照，不保存“单颗珠子”业务记录。每次读取方案时，
 * 服务端都会根据当前商品、尺寸、工艺费和绳材配置重新评估报价状态。</p>
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.auth.admin.username=admin",
        "app.auth.admin.password=Admin@123",
        "app.auth.jwt-secret=01234567890123456789012345678901",
        "app.auth.jwt-ttl=PT8H",
        "app.auth.jwt-issuer=diy-bracelet-test",
        "app.customer-auth.jwt-secret=customer-test-secret-0123456789012345",
        "app.customer-auth.jwt-ttl=P30D",
        "app.customer-auth.jwt-issuer=diy-customer-test",
        "app.customer-auth.identity-pepper=identity-test-pepper-0123456789012"
})
@Sql(statements = {
        "delete from diy_scheme",
        "delete from customer_identity",
        "delete from craft_fee_rule",
        "delete from cord_option",
        "delete from diy_rule_config",
        "update bead_sku set sale_price = 12.80, status = 'ON_SALE' where id = 2001",
        "update bead_sku set sale_price = 16.80, status = 'ON_SALE' where id = 2002",
        """
        insert into diy_rule_config (
            id,
            min_wrist_mm,
            max_wrist_mm,
            snug_allowance_mm,
            standard_allowance_mm,
            loose_allowance_mm,
            tolerance_mm,
            max_bead_count,
            config_version
        ) values (1, 120.00, 250.00, 5.00, 10.00, 15.00, 4.00, 60, 1);
        """,
        """
        insert into craft_fee_rule (
            id,
            min_bead_count,
            max_bead_count,
            fee,
            status,
            sort_order,
            rule_version
        ) values (5001, 1, 60, 10.00, 'ENABLED', 10, 1);
        """,
        """
        insert into cord_option (
            id,
            code,
            name,
            color_hex,
            extra_fee,
            status,
            sort_order,
            option_version
        ) values
            (6001, 'ELASTIC-CLEAR', '透明弹力绳', '#E8E4DC', 0.00, 'ENABLED', 10, 1),
            (6002, 'ELASTIC-BLACK', '黑色弹力绳', '#272523', 0.00, 'ENABLED', 20, 1),
            (6003, 'ELASTIC-BROWN', '棕色弹力绳', '#72513C', 0.00, 'ENABLED', 30, 1);
        """
})
class AppDiySchemeControllerTest extends MySqlIntegrationTest {

    @Test
    void concurrentCreatesMustRespectTheThreeSchemeLimit() throws Exception {
        String session = mockMvc.perform(post("/api/app/session/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"installationId\":\"9b4c9e91-e267-4515-aad5-3900f2e985a9\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(session).path("data").path("accessToken").asText();
        long customerId = objectMapper.readTree(session).path("data").path("customerIdentityId").asLong();
        String request = """
                {"name":"并发保存","wristCircumferenceMm":160,"fitType":"STANDARD",
                 "cordOptionId":6001,"sequence":[2001],"version":0}
                """;
        var executor = java.util.concurrent.Executors.newFixedThreadPool(8);
        var ready = new java.util.concurrent.CountDownLatch(8);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var results = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int index = 0; index < 8; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start barrier timed out");
                    }
                    return mockMvc.perform(post("/api/app/diy/schemes")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON).content(request))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var statuses = new ArrayList<Integer>();
            for (var result : results) statuses.add(result.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(statuses).containsOnly(201, 409);
            assertThat(statuses.stream().filter(value -> value == 201).count()).isEqualTo(3);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from diy_scheme where customer_identity_id = ? and status = 'ACTIVE'",
                    Integer.class, customerId)).isEqualTo(3);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateReadUpdateAndRejectStaleVersion() throws Exception {
        String customerToken = customerToken(
                "3f6f7816-0f1a-4aa0-870f-f34f9195a2ec"
        );
        List<Long> sequence = sequence(12, 9);

        Long schemeId = createScheme(customerToken, "我的第一串", sequence)
                .path("id")
                .asLong();

        mockMvc.perform(get("/api/app/diy/schemes/{id}", schemeId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sequence[0]").value(2001))
                .andExpect(jsonPath("$.data.sequence[12]").value(2002))
                .andExpect(jsonPath("$.data.beadCount").value(21))
                .andExpect(jsonPath("$.data.quoteStatus").value("VALID"))
                .andExpect(jsonPath("$.data.catalogSnapshot[0].color")
                        .value("透明"))
                .andExpect(jsonPath("$.data.totalAmount").value(314.80));

        mockMvc.perform(put("/api/app/diy/schemes/{id}", schemeId)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("我的第一串-修改", sequence(11, 10), 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.sequence[10]").value(2001))
                .andExpect(jsonPath("$.data.sequence[11]").value(2002));

        mockMvc.perform(put("/api/app/diy/schemes/{id}", schemeId)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("旧版本提交", sequence, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DIY_SCHEME_VERSION_CONFLICT"));
    }

    @Test
    void shouldRejectTheFourthActiveSchemeForOneCustomer() throws Exception {
        String customerToken = customerToken(
                "21efad2d-7dcb-4a69-9a4f-11b22fb90c82"
        );
        List<Long> sequence = sequence(12, 9);

        createScheme(customerToken, "方案一", sequence);
        createScheme(customerToken, "方案二", sequence);
        createScheme(customerToken, "方案三", sequence);

        mockMvc.perform(post("/api/app/diy/schemes")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("方案四", sequence, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("DIY_SCHEME_LIMIT_REACHED"));
    }

    @Test
    void shouldHideOtherCustomerSchemeAsNotFound() throws Exception {
        String ownerToken = customerToken(
                "3f6f7816-0f1a-4aa0-870f-f34f9195a2ec"
        );
        String otherToken = customerToken(
                "7b46d459-489d-45c8-8d08-d46aa9f592e5"
        );
        Long schemeId = createScheme(ownerToken, "我的第一串", sequence(12, 9))
                .path("id")
                .asLong();

        mockMvc.perform(get("/api/app/diy/schemes/{id}", schemeId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
    }

    @Test
    void shouldCopyAndSoftDeleteOwnedScheme() throws Exception {
        String customerToken = customerToken(
                "3f6f7816-0f1a-4aa0-870f-f34f9195a2ec"
        );
        Long schemeId = createScheme(customerToken, "我的第一串", sequence(12, 9))
                .path("id")
                .asLong();

        mockMvc.perform(post("/api/app/diy/schemes/{id}/copies", schemeId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("我的第一串 副本"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.quoteStatus").value("VALID"));

        mockMvc.perform(get("/api/app/diy/schemes")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(delete("/api/app/diy/schemes/{id}", schemeId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/app/diy/schemes/{id}", schemeId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/app/diy/schemes")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void shouldMarkSavedSchemeStaleWhenCurrentPriceChanges()
            throws Exception {
        String customerToken = customerToken(
                "3f6f7816-0f1a-4aa0-870f-f34f9195a2ec"
        );
        Long schemeId = createScheme(customerToken, "会变价的手串", sequence(12, 9))
                .path("id")
                .asLong();

        jdbcTemplate.update(
                "update bead_sku set sale_price = 13.80 where id = 2001"
        );

        mockMvc.perform(get("/api/app/diy/schemes/{id}", schemeId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quoteStatus").value("STALE"))
                .andExpect(jsonPath("$.data.totalAmount").value(326.80));
    }

    @Test
    void shouldPersistColorAndExposePublishedVisualInSchemeSnapshot()
            throws Exception {
        insertPublishedVisualForWhiteCrystal();
        String customerToken = customerToken(
                "3f6f7816-0f1a-4aa0-870f-f34f9195a2ec"
        );
        JsonNode response = createScheme(
                customerToken,
                "带视觉的方案",
                sequence(12, 9)
        );

        assertThat(response.path("catalogSnapshot").get(0).path("color")
                .asText()).isEqualTo("透明");
        assertThat(response.path("catalogSnapshot").get(0)
                .path("diyVisual").path("trayUrl").asText())
                .isEqualTo("/api/public/files/scheme-tray.png");
        String persistedSnapshot = jdbcTemplate.queryForObject(
                "select catalog_snapshot_json from diy_scheme where id = ?",
                String.class,
                response.path("id").asLong()
        );
        assertThat(objectMapper.readTree(persistedSnapshot).get(0)
                .path("color").asText()).isEqualTo("透明");
    }

    private void insertPublishedVisualForWhiteCrystal() {
        jdbcTemplate.update("""
                insert into asset_file (
                    id, storage_key, original_name, content_type,
                    size_bytes, public_url, status
                ) values
                    (981, 'scheme-tray.png', 'scheme-tray.png', 'image/png',
                     128, '/api/public/files/scheme-tray.png', 'ACTIVE'),
                    (982, 'scheme-canvas.png', 'scheme-canvas.png', 'image/png',
                     128, '/api/public/files/scheme-canvas.png', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                insert into bead_visual_version (
                    bead_sku_id, version_no, source_type, status,
                    tray_asset_id, canvas_asset_id, created_by
                ) values (2001, 1, 'MANUAL', 'PUBLISHED', 981, 982, 'test')
                """);
        Long versionId = jdbcTemplate.queryForObject("""
                select id from bead_visual_version
                where bead_sku_id = 2001 and tray_asset_id = 981
                """, Long.class);
        jdbcTemplate.update(
                "update bead_sku set published_visual_version_id = ? where id = 2001",
                versionId
        );
    }

    private JsonNode createScheme(
            String customerToken,
            String name,
            List<Long> sequence
    ) throws Exception {
        byte[] responseBody = mockMvc.perform(post("/api/app/diy/schemes")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(name, sequence, 0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.quoteStatus").value("VALID"))
                .andExpect(jsonPath("$.data.sequence[0]").value(2001))
                .andExpect(jsonPath("$.data.beadCount").value(21))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        return objectMapper.readTree(responseBody).path("data");
    }

    private String payload(
            String name,
            List<Long> sequence,
            long version
    ) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", name,
                "wristCircumferenceMm", 160,
                "fitType", "STANDARD",
                "cordOptionId", 6001,
                "sequence", sequence,
                "version", version
        ));
    }

    private List<Long> sequence(int whiteCrystalCount, int amethystCount) {
        List<Long> sequence = new ArrayList<>();
        sequence.addAll(Collections.nCopies(whiteCrystalCount, 2001L));
        sequence.addAll(Collections.nCopies(amethystCount, 2002L));
        return sequence;
    }

    private String customerToken(String installationId) throws Exception {
        String responseBody = mockMvc.perform(post("/api/app/session/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "installationId",
                                installationId
                        ))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);
        return response.path("data").path("accessToken").asText();
    }
}
