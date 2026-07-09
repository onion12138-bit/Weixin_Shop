package com.diybracelet.mall.identity.api;

import com.diybracelet.mall.identity.infrastructure.CustomerJwtService;
import com.diybracelet.mall.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 小程序匿名访客会话的集成测试。
 *
 * <p>测试覆盖三件事：同一安装身份重复交换时复用同一顾客身份、接口响应不泄露原始
 * 安装 UUID、所有小程序业务接口必须携带顾客 JWT。后续微信 OpenID 接入时，外部身份
 * 只替换交换逻辑，不改变受保护接口的认证契约。</p>
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.customer-auth.jwt-secret=customer-test-secret-0123456789012345",
        "app.customer-auth.jwt-ttl=P30D",
        "app.customer-auth.jwt-issuer=diy-customer-test",
        "app.customer-auth.identity-pepper=identity-test-pepper-0123456789012"
})
class GuestSessionControllerTest extends MySqlIntegrationTest {

    private static final String INSTALLATION_ID =
            "3f6f7816-0f1a-4aa0-870f-f34f9195a2ec";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerJwtService customerJwtService;

    @Test
    void shouldReuseGuestIdentityAndIssueCustomerToken() throws Exception {
        String body = """
                {"installationId":"%s"}
                """.formatted(INSTALLATION_ID);

        String first = mockMvc.perform(post("/api/app/session/guest")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(post("/api/app/session/guest")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from customer_identity",
                Integer.class
        );
        assertThat(count).isEqualTo(1);
        assertThat(first).doesNotContain(INSTALLATION_ID);
    }

    @Test
    void shouldProtectCustomerApiWithoutCustomerToken() throws Exception {
        mockMvc.perform(get("/api/app/diy/config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_UNAUTHORIZED"));
    }
}
