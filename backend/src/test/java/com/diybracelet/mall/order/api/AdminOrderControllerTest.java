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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理后台订单查询与履约状态机集成测试。
 *
 * <p>测试从顾客创建并模拟支付订单开始，再由管理员处理制作、发货和完成，
 * 覆盖三端实际串联的数据而不是直接伪造订单行。</p>
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
        "delete from order_operation_log",
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
})
@Sql(
        statements = {
                "delete from inventory_ledger where order_id is not null",
                "delete from order_operation_log",
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
class AdminOrderControllerTest extends MySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldListOrdersWithBomForAdministrator() throws Exception {
        Long orderId = createPaidOrder("6ba3c376-b1c9-41d2-a14d-f8fbfe6f7997");
        String adminToken = loginAsAdmin();

        mockMvc.perform(get("/api/admin/orders")
                        .param("status", "IN_PRODUCTION")
                        .param("orderNo", "ORD")
                        .param("contactName", "后台测试")
                        .param("contactPhone", "1360000")
                        .param("minAmount", "1")
                        .param("sortDirection", "desc")
                        .param("page", "1")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(orderId))
                .andExpect(jsonPath("$.data.items[0].status")
                        .value("IN_PRODUCTION"))
                .andExpect(jsonPath("$.data.items[0].items[0].skuId")
                        .value(2001))
                .andExpect(jsonPath("$.data.items[0].items[0].quantity")
                        .value(12))
                .andExpect(jsonPath("$.data.items[0].fulfillment.status")
                        .value("IN_PRODUCTION"));

        mockMvc.perform(get("/api/admin/orders/statistics")
                        .param("contactName", "后台测试")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.inProduction").value(1))
                .andExpect(jsonPath("$.data.pendingShipment").value(0));

        mockMvc.perform(get("/api/admin/orders/{orderId}", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product.name")
                        .value("后台订单测试方案"))
                .andExpect(jsonPath("$.data.payment.channel").value("MOCK"))
                .andExpect(jsonPath("$.data.operations.length()")
                        .value(3));
    }

    @Test
    void shouldCompleteProductionAndEnterPendingShipment() throws Exception {
        Long orderId = createPaidOrder("157454e9-f92b-4096-ab8d-1a70c710e4f8");
        String adminToken = loginAsAdmin();

        mockMvc.perform(post(
                        "/api/admin/orders/{orderId}/complete-production",
                        orderId
                ).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("PENDING_SHIPMENT"))
                .andExpect(jsonPath("$.data.fulfillment.status")
                        .value("PENDING_SHIPMENT"));

        assertThat(orderStatus(orderId)).isEqualTo("PENDING_SHIPMENT");
    }

    @Test
    void shouldShipProductionOrderAndSaveTrackingInformation()
            throws Exception {
        Long orderId = createPaidOrder("ce050697-a5a0-41cd-814d-2b24876feb35");
        String adminToken = loginAsAdmin();
        completeProduction(adminToken, orderId);

        mockMvc.perform(post("/api/admin/orders/{orderId}/ship", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logisticsCompany": "顺丰速运",
                                  "trackingNo": "SF-DEMO-20260711"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath(
                        "$.data.fulfillment.logisticsCompany"
                ).value("顺丰速运"))
                .andExpect(jsonPath("$.data.fulfillment.trackingNo")
                        .value("SF-DEMO-20260711"));

        assertThat(orderStatus(orderId)).isEqualTo("SHIPPED");
    }

    @Test
    void shouldCompleteShippedOrder() throws Exception {
        Long orderId = createPaidOrder("31b6e62c-790f-466e-9bc8-a38ee51c8ff7");
        String adminToken = loginAsAdmin();
        completeProduction(adminToken, orderId);
        ship(adminToken, orderId);

        mockMvc.perform(post("/api/admin/orders/{orderId}/complete", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.fulfillment.status")
                        .value("COMPLETED"));

        assertThat(orderStatus(orderId)).isEqualTo("COMPLETED");
    }

    @Test
    void shouldRejectShippingBeforeProductionStarts() throws Exception {
        Long orderId = createPaidOrder("7fb54879-414b-4f08-bc4b-1559305bd698");
        String adminToken = loginAsAdmin();

        mockMvc.perform(post("/api/admin/orders/{orderId}/ship", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logisticsCompany": "顺丰速运",
                                  "trackingNo": "SF-SKIP-STATE"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_CONFLICT"));

        assertThat(orderStatus(orderId)).isEqualTo("IN_PRODUCTION");
    }

    private String orderStatus(Long orderId) {
        return jdbcTemplate.queryForObject(
                "select status from customer_order where id = ?",
                String.class,
                orderId
        );
    }

    private void completeProduction(String adminToken, Long orderId)
            throws Exception {
        mockMvc.perform(post(
                        "/api/admin/orders/{orderId}/complete-production",
                        orderId
                ).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private void ship(String adminToken, Long orderId) throws Exception {
        mockMvc.perform(post("/api/admin/orders/{orderId}/ship", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logisticsCompany": "顺丰速运",
                                  "trackingNo": "SF-DEMO-COMPLETE"
                                }
                                """))
                .andExpect(status().isOk());
    }

    private Long createPaidOrder(String installationId) throws Exception {
        String customerToken = customerToken(installationId);
        Long schemeId = createScheme(customerToken);
        String orderBody = mockMvc.perform(post("/api/app/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "schemeId", schemeId,
                                "contactName", "后台测试顾客",
                                "contactPhone", "13600000000",
                                "addressDetail", "上海市测试区后台路 1 号"
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(orderBody)
                .path("data").path("id").asLong();
        mockMvc.perform(post("/api/app/orders/{orderId}/mock-pay", orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
        return orderId;
    }

    private Long createScheme(String token) throws Exception {
        String responseBody = mockMvc.perform(post("/api/app/diy/schemes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "后台订单测试方案",
                                "wristCircumferenceMm", 160,
                                "fitType", "STANDARD",
                                "cordOptionId", 6001,
                                "sequence", sequence(),
                                "version", 0
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(responseBody)
                .path("data").path("id").asLong();
    }

    private List<Long> sequence() {
        List<Long> sequence = new ArrayList<>();
        sequence.addAll(Collections.nCopies(12, 2001L));
        sequence.addAll(Collections.nCopies(9, 2002L));
        return sequence;
    }

    private String customerToken(String installationId) throws Exception {
        String responseBody = mockMvc.perform(post("/api/app/session/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "installationId", installationId
                        ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(responseBody)
                .path("data").path("accessToken").asText();
    }

    private String loginAsAdmin() throws Exception {
        String responseBody = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "Admin@123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);
        return response.path("data").path("accessToken").asText();
    }
}
