package com.diybracelet.mall.order.api;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 小程序订单接口集成测试。
 *
 * <p>订单必须基于当前顾客拥有的 DIY 方案创建。创建时重新读取服务端方案、SKU、
 * 价格和库存，不信任小程序提交的金额，成功后立即预占库存。</p>
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
        "delete from inventory_ledger where order_id is not null",
        "delete from fulfillment_task",
        "delete from payment_record",
        "delete from order_item",
        "delete from customer_order",
        "delete from diy_scheme",
        "delete from customer_identity",
        """
        update inventory_balance
        set available_qty = 200,
            reserved_qty = 0,
            sold_qty = 0,
            version = 0
        where bead_sku_id in (2001, 2002, 2003, 2004);
        """,
        "update bead_sku set sale_price = 12.80, status = 'ON_SALE' where id = 2001",
        "update bead_sku set sale_price = 16.80, status = 'ON_SALE' where id = 2002"
})
@Sql(
        statements = {
                "delete from inventory_ledger where order_id is not null",
                "delete from fulfillment_task",
                "delete from payment_record",
                "delete from order_item",
                "delete from customer_order",
                "delete from diy_scheme",
                "delete from customer_identity",
                """
                update inventory_balance
                set available_qty = 200,
                    reserved_qty = 0,
                    sold_qty = 0,
                    version = 0
                where bead_sku_id in (2001, 2002, 2003, 2004);
                """
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class AppOrderControllerTest extends MySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreatePendingOrderAndReserveInventoryFromSavedScheme()
            throws Exception {
        String token = customerToken("3f6f7816-0f1a-4aa0-870f-f34f9195a2ec");
        Long schemeId = createScheme(token, sequence(12, 9));

        mockMvc.perform(post("/api/app/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "schemeId", schemeId,
                                "contactName", "张三",
                                "contactPhone", "13800000000",
                                "addressDetail", "上海市浦东新区演示路 1 号"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.totalAmount").value(314.80))
                .andExpect(jsonPath("$.data.items[0].skuId").value(2001))
                .andExpect(jsonPath("$.data.items[0].quantity").value(12))
                .andExpect(jsonPath("$.data.items[1].skuId").value(2002))
                .andExpect(jsonPath("$.data.items[1].quantity").value(9));

        Map<String, Object> whiteCrystal = inventory(2001L);
        Map<String, Object> amethyst = inventory(2002L);
        assertThat(whiteCrystal)
                .containsEntry("available_qty", 188)
                .containsEntry("reserved_qty", 12)
                .containsEntry("sold_qty", 0);
        assertThat(amethyst)
                .containsEntry("available_qty", 191)
                .containsEntry("reserved_qty", 9)
                .containsEntry("sold_qty", 0);
    }

    @Test
    void shouldRejectOrderWhenInventoryIsInsufficient() throws Exception {
        String token = customerToken("7b46d459-489d-45c8-8d08-d46aa9f592e5");
        Long schemeId = createScheme(token, sequence(12, 9));
        jdbcTemplate.update("""
                update inventory_balance
                set available_qty = 1,
                    reserved_qty = 0,
                    sold_qty = 0
                where bead_sku_id = 2001
                """);

        mockMvc.perform(post("/api/app/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "schemeId", schemeId,
                                "contactName", "李四",
                                "contactPhone", "13900000000",
                                "addressDetail", "上海市徐汇区演示路 2 号"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_INSUFFICIENT"));

        Integer orderCount = jdbcTemplate.queryForObject(
                "select count(*) from customer_order",
                Integer.class
        );
        Map<String, Object> whiteCrystal = inventory(2001L);
        assertThat(orderCount).isZero();
        assertThat(whiteCrystal)
                .containsEntry("available_qty", 1)
                .containsEntry("reserved_qty", 0)
                .containsEntry("sold_qty", 0);
    }

    private Map<String, Object> inventory(Long skuId) {
        return jdbcTemplate.queryForMap("""
                select available_qty, reserved_qty, sold_qty
                from inventory_balance
                where bead_sku_id = ?
                """, skuId);
    }

    private Long createScheme(String token, List<Long> sequence)
            throws Exception {
        String responseBody = mockMvc.perform(post("/api/app/diy/schemes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "订单测试方案",
                                "wristCircumferenceMm", 160,
                                "fitType", "STANDARD",
                                "cordOptionId", 6001,
                                "sequence", sequence,
                                "version", 0
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);
        return response.path("data").path("id").asLong();
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
