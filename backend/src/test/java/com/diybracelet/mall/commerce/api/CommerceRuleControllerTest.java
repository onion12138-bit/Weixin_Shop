package com.diybracelet.mall.commerce.api;

import com.diybracelet.mall.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@Sql(statements = "update commerce_rule_config set minimum_order_amount = 20.00, "
        + "free_shipping_threshold = 60.00, base_shipping_fee = 8.00, "
        + "config_version = 1 where id = 1")
@Sql(statements = "update commerce_rule_config set minimum_order_amount = 20.00, "
        + "free_shipping_threshold = 60.00, base_shipping_fee = 8.00, "
        + "config_version = 1 where id = 1",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class CommerceRuleControllerTest extends MySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeAndSaveCommerceRules() throws Exception {
        mockMvc.perform(get("/api/app/commerce/config")
                        .header("Authorization", "Bearer " + customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minimumOrderAmount").value(20.00))
                .andExpect(jsonPath("$.data.freeShippingThreshold").value(60.00))
                .andExpect(jsonPath("$.data.baseShippingFee").value(8.00));

        mockMvc.perform(put("/api/admin/commerce/config")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "minimumOrderAmount":30.00,
                                  "freeShippingThreshold":80.00,
                                  "baseShippingFee":10.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minimumOrderAmount").value(30.00))
                .andExpect(jsonPath("$.data.freeShippingThreshold").value(80.00))
                .andExpect(jsonPath("$.data.baseShippingFee").value(10.00))
                .andExpect(jsonPath("$.data.configVersion").value(2));
    }

    private String customerToken() throws Exception {
        String response = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/app/session/guest")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"installationId\":"
                                        + "\"8b111d7f-025a-4f97-8888-000000002603\"}")
                )
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).path("data").path("accessToken").asText();
    }

    private String adminToken() throws Exception {
        String response = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/admin/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}")
                )
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).path("data").path("accessToken").asText();
    }
}
