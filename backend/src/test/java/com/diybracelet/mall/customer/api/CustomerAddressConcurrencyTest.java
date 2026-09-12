package com.diybracelet.mall.customer.api;

import com.diybracelet.mall.identity.application.GuestIdentityService;
import com.diybracelet.mall.identity.api.dto.GuestSessionResponse;
import com.diybracelet.mall.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class CustomerAddressConcurrencyTest extends MySqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired GuestIdentityService guests;
    @Autowired JdbcTemplate jdbc;
    private GuestSessionResponse customer;

    @AfterEach
    void removeOwnFixtures() {
        if (customer != null) {
            jdbc.update("delete from customer_address where customer_identity_id = ?", customer.customerIdentityId());
            jdbc.update("delete from customer_identity where id = ?", customer.customerIdentityId());
        }
    }

    @Test
    void concurrentDefaultAddressCreatesMustLeaveExactlyOneDefault() throws Exception {
        customer = guests.exchange(UUID.randomUUID().toString());
        var executor = Executors.newFixedThreadPool(8);
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        try {
            var results = new ArrayList<Future<Integer>>();
            for (int index = 0; index < 8; index++) {
                results.add(executor.submit(() -> {
                    // Warm and reserve a distinct database connection per
                    // worker so connection startup cannot serialize the race.
                    jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
                        ready.countDown();
                        try {
                            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start barrier timed out");
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(error);
                        }
                        return null;
                    });
                    return mvc.perform(post("/api/app/addresses")
                                    .header("Authorization", "Bearer " + customer.accessToken())
                                    .contentType(MediaType.APPLICATION_JSON).content("""
                                            {"receiverName":"顾客","phone":"13800000000",
                                             "region":"上海市","detail":"地址并发回归","isDefault":true}
                                            """))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var result : results) assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(201);
            assertThat(jdbc.queryForObject("""
                    select count(*) from customer_address
                    where customer_identity_id = ? and status = 'ACTIVE' and is_default = true
                    """, Integer.class, customer.customerIdentityId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from customer_address where customer_identity_id = ?",
                    Integer.class, customer.customerIdentityId())).isEqualTo(8);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
