package com.diybracelet.mall.shared.health;

import com.diybracelet.mall.auth.infrastructure.JwtAuthenticationFilter;
import com.diybracelet.mall.auth.infrastructure.JwtService;
import com.diybracelet.mall.auth.infrastructure.SecurityConfiguration;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 验证无需数据库即可使用的项目健康接口契约。
 */
@WebMvcTest(ProjectHealthController.class)
@Import({
    JwtService.class,
    JwtAuthenticationFilter.class,
    SecurityConfiguration.class
})
@TestPropertySource(properties = {
    "app.auth.jwt-secret=01234567890123456789012345678901",
    "app.auth.jwt-ttl=PT8H",
    "app.auth.jwt-issuer=diy-bracelet-test"
})
class ProjectHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeProjectIdentity() throws Exception {
        mockMvc.perform(get("/api/public/project-health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("DIY 手串定制商城"))
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
