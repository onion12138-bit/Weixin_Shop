package com.diybracelet.mall.customer.api;

import com.diybracelet.mall.support.MySqlIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实数据库验证已发权益不漂移、发放重试不会额外发券。 */
@Transactional
@AutoConfigureMockMvc
class CouponReliabilityTest extends MySqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void issuedCouponKeepsRulesWhenTemplateChangesOrStopsIssuing() throws Exception {
        String admin = admin();
        JsonNode customer = customer();
        long templateId = template(admin);
        grant(admin, templateId, Map.of("customerIdentityId", customer.path("customerIdentityId").asLong(),
                "note", "原规则", "batchId", UUID.randomUUID().toString()), false);
        mvc.perform(put("/api/admin/coupons/" + templateId).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "code", "CHANGED-" + UUID.randomUUID().toString().substring(0,8).toUpperCase(),
                        "name", "新券", "description", "已改规则", "discountAmount", 2,
                        "minimumSpend", 999, "validDays", 1, "status", "INACTIVE"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/app/coupons").header("Authorization", "Bearer " + customer.path("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("原始权益券"))
                .andExpect(jsonPath("$.data[0].discountAmount").value(10))
                .andExpect(jsonPath("$.data[0].minimumSpend").value(20));
    }

    @Test
    void repeatingTheSameBatchReturnsOriginalResultAndRejectsChangedRecipients() throws Exception {
        String admin = admin();
        long first = customer().path("customerIdentityId").asLong();
        long second = customer().path("customerIdentityId").asLong();
        long templateId = template(admin);
        String batch = UUID.randomUUID().toString();
        var payload = Map.of("recipientMode", "SELECTED", "customerIdentityIds", List.of(first), "note", "", "batchId", batch);
        JsonNode initial = grant(admin, templateId, payload, true);
        assertThat(grant(admin, templateId, payload, true)).isEqualTo(initial);
        assertThat(jdbc.queryForObject("select count(*) from customer_coupon where coupon_template_id = ?", Integer.class, templateId)).isEqualTo(1);
        mvc.perform(post("/api/admin/coupons/" + templateId + "/grants/batch").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "recipientMode", "SELECTED", "customerIdentityIds", List.of(second), "note", "", "batchId", batch))))
                .andExpect(status().isConflict());
    }

    @Test
    void singleGrantRetryReturnsSameCouponId() throws Exception {
        String admin = admin();
        long id = customer().path("customerIdentityId").asLong();
        long templateId = template(admin);
        var payload = Map.of("customerIdentityId", id, "note", "", "batchId", UUID.randomUUID().toString());
        JsonNode first = grant(admin, templateId, payload, false);
        assertThat(grant(admin, templateId, payload, false).path("id")).isEqualTo(first.path("id"));
        assertThat(jdbc.queryForObject("select count(*) from customer_coupon where coupon_template_id = ?", Integer.class, templateId)).isEqualTo(1);
    }

    @Test
    void allRecipientsRemainFrozenAcrossRetriesAfterNewCustomerArrives() throws Exception {
        String admin = admin();
        customer();
        long templateId = template(admin);
        var payload = Map.of("recipientMode", "ALL", "customerIdentityIds", List.of(), "note", "", "batchId", UUID.randomUUID().toString());
        JsonNode initial = grant(admin, templateId, payload, true);
        long lateCustomer = customer().path("customerIdentityId").asLong();
        jdbc.update("update coupon_template set status = 'INACTIVE' where id = ?", templateId);
        assertThat(grant(admin, templateId, payload, true)).isEqualTo(initial);
        assertThat(jdbc.queryForObject("select count(*) from customer_coupon where coupon_template_id = ?", Integer.class, templateId))
                .isEqualTo(initial.path("grantedCount").asInt());
        assertThat(jdbc.queryForObject("select count(*) from customer_coupon where coupon_template_id = ? and customer_identity_id = ?", Integer.class, templateId, lateCustomer)).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRetriesCommitOnlyOneGrantPerRecipient() throws Exception {
        String admin = admin();
        long recipient = customer().path("customerIdentityId").asLong();
        long templateId = template(admin);
        String batch = UUID.randomUUID().toString();
        var payload = Map.of("recipientMode", "SELECTED", "customerIdentityIds", List.of(recipient), "note", "并发", "batchId", batch);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<JsonNode> call = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("启动屏障超时");
                return grant(admin, templateId, payload, true);
            };
            var first = pool.submit(call);
            var second = pool.submit(call);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("select count(*) from customer_coupon where grant_batch_id = ?", Integer.class, batch)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select granted_count from coupon_grant_batch where batch_id = ?", Integer.class, batch)).isEqualTo(1);
        } finally {
            start.countDown();
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            jdbc.update("delete from customer_coupon where coupon_template_id = ?", templateId);
            jdbc.update("delete from coupon_grant_batch where batch_id = ?", batch);
            jdbc.update("delete from coupon_template where id = ?", templateId);
            jdbc.update("delete from customer_identity where id = ?", recipient);
        }
    }

    private JsonNode grant(String admin, long templateId, Map<String, ?> payload, boolean batch) throws Exception {
        return data(mvc.perform(post("/api/admin/coupons/" + templateId + "/grants" + (batch ? "/batch" : ""))
                .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private String admin() throws Exception {
        return "Bearer " + data(mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }
    private JsonNode customer() throws Exception {
        return data(mvc.perform(post("/api/app/session/guest").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("installationId", UUID.randomUUID().toString()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private long template(String admin) throws Exception {
        return data(mvc.perform(post("/api/admin/coupons").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "code", "RULE-" + UUID.randomUUID().toString().substring(0,8).toUpperCase(),
                        "name", "原始权益券", "description", "原始规则", "discountAmount", 10,
                        "minimumSpend", 20, "validDays", 30, "status", "ACTIVE"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    }
    private JsonNode data(String body) throws Exception { return json.readTree(body).path("data"); }
}
