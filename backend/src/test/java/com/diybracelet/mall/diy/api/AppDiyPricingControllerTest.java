package com.diybracelet.mall.diy.api;

import com.diybracelet.mall.support.MySqlIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 小程序 DIY 权威报价接口集成测试。
 *
 * <p>测试使用真实 MySQL 演示 SKU 和完整安全链，验证后端只相信 SKU ID、顺序、腕围、
 * 松紧和绳材，最终价格由服务端当前配置与商品价格重新计算。</p>
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
        "delete from craft_fee_rule",
        "delete from cord_option",
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
        """
})
class AppDiyPricingControllerTest extends MySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldQuoteValidStandardDesignWithServerPrices() throws Exception {
        List<Long> sequence = new ArrayList<>();
        sequence.addAll(Collections.nCopies(12, 2001L));
        sequence.addAll(Collections.nCopies(9, 2002L));

        mockMvc.perform(post("/api/app/diy/quotes")
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "wristCircumferenceMm", 160,
                                "fitType", "STANDARD",
                                "cordOptionId", 6001,
                                "sequence", sequence
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotable").value(true))
                .andExpect(jsonPath("$.data.currentLengthMm").value(168.00))
                .andExpect(jsonPath("$.data.targetLengthMm").value(170.00))
                .andExpect(jsonPath("$.data.sizeStatus").value("FIT"))
                .andExpect(jsonPath("$.data.beadAmount").value(304.80))
                .andExpect(jsonPath("$.data.craftFee").value(10.00))
                .andExpect(jsonPath("$.data.cordFee").value(0.00))
                .andExpect(jsonPath("$.data.totalAmount").value(314.80))
                .andExpect(jsonPath("$.data.quoteFingerprint").isNotEmpty());
    }

    @Test
    void shouldReturnRepairableIssueForUnavailableSku() throws Exception {
        mockMvc.perform(post("/api/app/diy/quotes")
                        .header("Authorization", "Bearer " + customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "wristCircumferenceMm", 160,
                                "fitType", "STANDARD",
                                "cordOptionId", 6001,
                                "sequence", List.of(999999L)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quotable").value(false))
                .andExpect(jsonPath("$.data.issues[0].code")
                        .value("SKU_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.issues[0].skuId")
                        .value(999999));
    }

    private String customerToken() throws Exception {
        String responseBody = mockMvc.perform(post("/api/app/session/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"installationId":"3f6f7816-0f1a-4aa0-870f-f34f9195a2ec"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);
        return response.path("data").path("accessToken").asText();
    }
}
