package com.diybracelet.mall.diy.api;

import com.diybracelet.mall.auth.infrastructure.JwtService;
import com.diybracelet.mall.diy.api.dto.SaveDiyVisualConfigRequest;
import com.diybracelet.mall.diy.application.DiyConfigurationService;
import com.diybracelet.mall.diy.domain.DiyVisualConfig;
import com.diybracelet.mall.diy.infrastructure.mapper.DiyVisualConfigMapper;
import com.diybracelet.mall.support.MySqlIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DIY 尺寸、工艺费和绳材配置接口集成测试。
 *
 * <p>使用真实 MySQL 和完整安全链，确保顾客端只能读取启用配置，管理端可以维护配置，
 * 且工艺费区间校验在服务端强制执行。</p>
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
        "delete from craft_fee_rule",
        "delete from cord_option",
        "delete from diy_visual_config",
        "delete from diy_rule_config",
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
        """,
        """
        insert into diy_visual_config (
            id,
            canvas_background_color,
            bracelet_shadow_opacity,
            bead_shadow_opacity,
            bead_overlap_ratio,
            config_version
        ) values (1, '#fffdfb', 0.160, 0.200, 0.060, 1);
        """
})
class DiyConfigurationControllerTest extends MySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DiyConfigurationService diyConfigurationService;

    @SpyBean
    private DiyVisualConfigMapper visualConfigMapper;

    @Test
    void shouldReturnPublicConfigurationForCustomer() throws Exception {
        mockMvc.perform(get("/api/app/diy/config")
                        .header("Authorization", "Bearer " + customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.rule.maxBeadCount").value(60))
                .andExpect(jsonPath("$.data.rule.configVersion").value(1))
                .andExpect(jsonPath("$.data.visualConfig.canvasBackgroundColor")
                        .value("#fffdfb"))
                .andExpect(jsonPath("$.data.visualConfig.configVersion").value(1))
                .andExpect(jsonPath("$.data.craftFeeRules.length()").value(1))
                .andExpect(jsonPath("$.data.cordOptions.length()").value(3))
                .andExpect(jsonPath("$.data.cordOptions[0].status")
                        .doesNotExist());
    }

    @Test
    void shouldRejectOverlappingCraftFeeRules() throws Exception {
        mockMvc.perform(put("/api/admin/diy/craft-fees")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    {"minBeadCount":1,"maxBeadCount":20,"fee":8.00},
                                    {"minBeadCount":20,"maxBeadCount":60,"fee":12.00}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("工艺费区间不能重叠"));
    }

    @Test
    void shouldRejectCraftFeeGap() throws Exception {
        mockMvc.perform(put("/api/admin/diy/craft-fees")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    {"minBeadCount":1,"maxBeadCount":20,"fee":8.00},
                                    {"minBeadCount":22,"maxBeadCount":60,"fee":12.00}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("工艺费区间不能留空档"));
    }

    @Test
    void shouldReplaceCraftFeeRulesAndIncrementVersion() throws Exception {
        mockMvc.perform(put("/api/admin/diy/craft-fees")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rules": [
                                    {"minBeadCount":1,"maxBeadCount":20,"fee":8.00},
                                    {"minBeadCount":21,"maxBeadCount":60,"fee":12.00}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.craftFeeRules.length()").value(2))
                .andExpect(jsonPath("$.data.craftFeeRules[0].ruleVersion")
                        .value(2))
                .andExpect(jsonPath("$.data.craftFeeRules[1].ruleVersion")
                        .value(2));
    }

    @Test
    void shouldSaveRuleConfigAndIncrementVersion() throws Exception {
        mockMvc.perform(put("/api/admin/diy/config")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "minWristMm":120.00,
                                  "maxWristMm":250.00,
                                  "snugAllowanceMm":5.00,
                                  "standardAllowanceMm":10.00,
                                  "looseAllowanceMm":15.00,
                                  "toleranceMm":5.00,
                                  "maxBeadCount":60
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rule.toleranceMm").value(5.00))
                .andExpect(jsonPath("$.data.rule.configVersion").value(2));
    }

    @Test
    void shouldSaveVisualConfigAndIncrementVersion() throws Exception {
        mockMvc.perform(put("/api/admin/diy/visual-config")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canvasBackgroundColor":"#f8f3ef",
                                  "braceletShadowOpacity":0.18,
                                  "beadShadowOpacity":0.22,
                                  "beadOverlapRatio":0.08
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visualConfig.canvasBackgroundColor")
                        .value("#f8f3ef"))
                .andExpect(jsonPath("$.data.visualConfig.configVersion").value(2));
    }

    @Test
    void shouldIncrementVisualVersionForBothConcurrentSuccessfulSaves()
            throws Exception {
        CountDownLatch bothReadsFinished = new CountDownLatch(2);
        AtomicInteger coordinatedReads = new AtomicInteger();
        doAnswer(invocation -> {
            DiyVisualConfig result = readVisualConfig();
            if (coordinatedReads.incrementAndGet() <= 2) {
                bothReadsFinished.countDown();
                awaitBriefly(bothReadsFinished);
            }
            return result;
        }).when(visualConfigMapper).selectById(1L);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        SaveDiyVisualConfigRequest request = new SaveDiyVisualConfigRequest(
                "#f8f3ef",
                new BigDecimal("0.18"),
                new BigDecimal("0.22"),
                new BigDecimal("0.08")
        );
        try {
            Future<?> first = executor.submit(() -> {
                ready.countDown();
                await(start);
                diyConfigurationService.saveVisualConfig(request, "admin-one");
            });
            Future<?> second = executor.submit(() -> {
                ready.countDown();
                await(start);
                diyConfigurationService.saveVisualConfig(request, "admin-two");
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            Long configVersion = jdbcTemplate.queryForObject(
                    "select config_version from diy_visual_config where id = 1",
                    Long.class
            );
            assertThat(configVersion).isEqualTo(3L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectVisualConfigOverlapRatioAboveMaximum() throws Exception {
        mockMvc.perform(put("/api/admin/diy/visual-config")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canvasBackgroundColor":"#f8f3ef",
                                  "braceletShadowOpacity":0.18,
                                  "beadShadowOpacity":0.22,
                                  "beadOverlapRatio":0.16
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectVisualConfigWithOmittedFieldWithoutPersistingChanges()
            throws Exception {
        mockMvc.perform(put("/api/admin/diy/visual-config")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canvasBackgroundColor":"#f8f3ef",
                                  "braceletShadowOpacity":0.18,
                                  "beadOverlapRatio":0.08
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertPersistedVisualConfigIsUnchanged();
    }

    @Test
    void shouldRejectVisualConfigWithExplicitNullWithoutPersistingChanges()
            throws Exception {
        mockMvc.perform(put("/api/admin/diy/visual-config")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canvasBackgroundColor":"#f8f3ef",
                                  "braceletShadowOpacity":0.18,
                                  "beadShadowOpacity":null,
                                  "beadOverlapRatio":0.08
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertPersistedVisualConfigIsUnchanged();
    }

    private void assertPersistedVisualConfigIsUnchanged() {
        Map<String, Object> visualConfig = jdbcTemplate.queryForMap("""
                select canvas_background_color, bracelet_shadow_opacity,
                    bead_shadow_opacity, bead_overlap_ratio, config_version
                from diy_visual_config
                where id = 1
                """);
        assertThat(visualConfig.get("canvas_background_color")).isEqualTo("#fffdfb");
        assertThat(visualConfig.get("bracelet_shadow_opacity"))
                .isEqualTo(new BigDecimal("0.160"));
        assertThat(visualConfig.get("bead_shadow_opacity"))
                .isEqualTo(new BigDecimal("0.200"));
        assertThat(visualConfig.get("bead_overlap_ratio"))
                .isEqualTo(new BigDecimal("0.060"));
        assertThat(visualConfig.get("config_version")).isEqualTo(1L);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发保存未按时开始");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发保存被中断", error);
        }
    }

    private static void awaitBriefly(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发读取被中断", error);
        }
    }

    private DiyVisualConfig readVisualConfig() {
        return jdbcTemplate.queryForObject("""
                select id, canvas_background_color,
                    bracelet_shadow_opacity, bead_shadow_opacity,
                    bead_overlap_ratio, config_version, updated_by,
                    created_at, updated_at
                from diy_visual_config
                where id = 1
                """, (resultSet, rowNumber) -> {
            DiyVisualConfig config = new DiyVisualConfig();
            config.setId(resultSet.getLong("id"));
            config.setCanvasBackgroundColor(
                    resultSet.getString("canvas_background_color")
            );
            config.setBraceletShadowOpacity(
                    resultSet.getBigDecimal("bracelet_shadow_opacity")
            );
            config.setBeadShadowOpacity(
                    resultSet.getBigDecimal("bead_shadow_opacity")
            );
            config.setBeadOverlapRatio(
                    resultSet.getBigDecimal("bead_overlap_ratio")
            );
            config.setConfigVersion(resultSet.getLong("config_version"));
            config.setUpdatedBy(resultSet.getString("updated_by"));
            if (resultSet.getTimestamp("created_at") != null) {
                config.setCreatedAt(
                        resultSet.getTimestamp("created_at").toLocalDateTime()
                );
            }
            if (resultSet.getTimestamp("updated_at") != null) {
                config.setUpdatedAt(
                        resultSet.getTimestamp("updated_at").toLocalDateTime()
                );
            }
            return config;
        });
    }

    private String customerToken() throws Exception {
        String body = """
                {"installationId":"3f6f7816-0f1a-4aa0-870f-f34f9195a2ec"}
                """;
        String responseBody = mockMvc.perform(post("/api/app/session/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);
        return response.path("data").path("accessToken").asText();
    }

    private String adminToken() {
        return jwtService.issueAccessToken("admin");
    }
}
