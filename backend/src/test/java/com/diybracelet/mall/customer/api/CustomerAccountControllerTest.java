package com.diybracelet.mall.customer.api;

import com.diybracelet.mall.support.MySqlIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@Rollback
@AutoConfigureMockMvc
class CustomerAccountControllerTest extends MySqlIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void shouldManageProfileAddressesAndGrantCoupon() throws Exception {
        CustomerSession customer = customerSession(
                "85dc1772-917c-4880-b680-f33303890f6c"
        );

        mockMvc.perform(put("/api/app/profile")
                        .header("Authorization", bearer(customer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"星月旅人","avatarAssetId":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("星月旅人"));

        String firstAddress = mockMvc.perform(post("/api/app/addresses")
                        .header("Authorization", bearer(customer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receiverName":"林小姐",
                                  "phone":"13800000001",
                                  "region":"上海市 徐汇区",
                                  "detail":"灵感路 8 号",
                                  "isDefault":false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andReturn().getResponse().getContentAsString();
        long addressId = objectMapper.readTree(firstAddress)
                .path("data").path("id").asLong();

        String adminToken = adminToken();
        mockMvc.perform(get("/api/admin/customers")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nickname").value("星月旅人"))
                .andExpect(jsonPath("$.data[0].phone").value("13800000001"))
                .andExpect(jsonPath("$.data[0].address")
                        .value("上海市 徐汇区 灵感路 8 号"));

        mockMvc.perform(get("/api/admin/customer-addresses")
                        .header("Authorization", bearer(adminToken))
                        .param("keyword", "灵感路"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].customerNickname")
                        .value("星月旅人"));

        mockMvc.perform(patch(
                        "/api/admin/customer-addresses/{id}/status",
                        addressId
                ).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELETED"));

        String templateBody = mockMvc.perform(post("/api/admin/coupons")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"CARE-20",
                                  "name":"心意关怀券",
                                  "description":"下单时可享立减",
                                  "discountAmount":20.00,
                                  "minimumSpend":100.00,
                                  "validDays":30,
                                  "status":"ACTIVE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long templateId = objectMapper.readTree(templateBody)
                .path("data").path("id").asLong();

        mockMvc.perform(post("/api/admin/coupons/{id}/grants", templateId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerIdentityId", customer.id(),
                                "batchId", java.util.UUID.randomUUID().toString(),
                                "note", "客服关怀"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("UNUSED"));

        mockMvc.perform(get("/api/app/coupons")
                        .header("Authorization", bearer(customer.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("心意关怀券"))
                .andExpect(jsonPath("$.data[0].discountAmount").value(20.00))
                .andExpect(jsonPath("$.data[0].note").value("客服关怀"));
    }

    @Test
    void shouldKeepAddressesIsolatedByCustomer() throws Exception {
        CustomerSession owner = customerSession(
                "bb97ba02-b314-40ed-b832-e5d4c454cd8a"
        );
        CustomerSession other = customerSession(
                "91fb102b-73aa-4bd6-9983-e720c1a0c516"
        );
        String addressBody = mockMvc.perform(post("/api/app/addresses")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "receiverName":"顾客甲",
                                  "phone":"13900000001",
                                  "region":"北京市 朝阳区",
                                  "detail":"测试路 1 号",
                                  "isDefault":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long addressId = objectMapper.readTree(addressBody)
                .path("data").path("id").asLong();

        mockMvc.perform(put("/api/app/addresses/{id}/default", addressId)
                        .header("Authorization", bearer(other.token())))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldSearchCustomersByPhoneAndGrantCouponsInBatches() throws Exception {
        CustomerSession first = customerSession(
                "1de7f772-3610-4ab4-b1c4-6cb17ed73795"
        );
        CustomerSession second = customerSession(
                "c878beae-fabe-4cc8-bd73-edb8046ad4ee"
        );
        createAddress(first, "顾客甲", "13912345001", "搜索路 1 号");
        createAddress(second, "顾客乙", "13912345002", "搜索路 2 号");

        String adminToken = adminToken();
        mockMvc.perform(get("/api/admin/customers/search")
                        .header("Authorization", bearer(adminToken))
                        .param("phone", "3450")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/admin/customer-addresses")
                        .header("Authorization", bearer(adminToken))
                        .param("customerIdentityId", String.valueOf(first.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath(
                        "$.data[*].customerIdentityId",
                        everyItem(is((int) first.id()))
                ));

        String templateBody = mockMvc.perform(post("/api/admin/coupons")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"BATCH-15",
                                  "name":"批量关怀券",
                                  "description":"用于验证多选与全量发放",
                                  "discountAmount":15.00,
                                  "minimumSpend":80.00,
                                  "validDays":15,
                                  "status":"ACTIVE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long templateId = objectMapper.readTree(templateBody)
                .path("data").path("id").asLong();

        mockMvc.perform(post(
                        "/api/admin/coupons/{id}/grants/batch",
                        templateId
                ).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "batchId", java.util.UUID.randomUUID().toString(),
                                "recipientMode", "SELECTED",
                                "customerIdentityIds", java.util.List.of(
                                        first.id(), second.id()
                                ),
                                "note", "多选发送"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requestedCount").value(2))
                .andExpect(jsonPath("$.data.grantedCount").value(2))
                .andExpect(jsonPath("$.data.skippedCount").value(0));

        mockMvc.perform(post(
                        "/api/admin/coupons/{id}/grants/batch",
                        templateId
                ).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "batchId":"test-all-grant-1234567890",
                                  "recipientMode":"ALL",
                                  "customerIdentityIds":[],
                                  "note":"全量发送"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requestedCount")
                        .value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.grantedCount")
                        .value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.skippedCount").value(0));
    }

    private void createAddress(
            CustomerSession customer,
            String receiverName,
            String phone,
            String detail
    ) throws Exception {
        mockMvc.perform(post("/api/app/addresses")
                        .header("Authorization", bearer(customer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "receiverName", receiverName,
                                "phone", phone,
                                "region", "上海市 浦东新区",
                                "detail", detail,
                                "isDefault", true
                        ))))
                .andExpect(status().isCreated());
    }

    private CustomerSession customerSession(String installationId)
            throws Exception {
        String body = mockMvc.perform(post("/api/app/session/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "installationId", installationId
                        ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        return new CustomerSession(
                data.path("customerIdentityId").asLong(),
                data.path("accessToken").asText()
        );
    }

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"Admin@123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data")
                .path("accessToken").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private record CustomerSession(long id, String token) { }
}
