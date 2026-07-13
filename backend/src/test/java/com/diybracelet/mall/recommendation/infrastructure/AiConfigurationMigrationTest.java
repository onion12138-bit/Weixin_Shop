package com.diybracelet.mall.recommendation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.diybracelet.mall.support.MySqlIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AiConfigurationMigrationTest extends MySqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSeedOnlyTheDisabledDeepSeekSingletonWithoutAnApiKey() {
        Integer rowCount = jdbcTemplate.queryForObject(
            "select count(*) from ai_model_config",
            Integer.class
        );
        Map<String, Object> config = jdbcTemplate.queryForMap("""
            select id, provider, base_url, model_name, encrypted_api_key, enabled, version
            from ai_model_config
            where id = 1
            """);

        assertThat(rowCount).isEqualTo(1);
        assertThat(((Number) config.get("id")).longValue()).isEqualTo(1L);
        assertThat(config.get("provider")).isEqualTo("DEEPSEEK");
        assertThat(config.get("base_url")).isEqualTo("https://api.deepseek.com");
        assertThat(config.get("model_name")).isEqualTo("deepseek-v4-flash");
        assertThat(config.get("encrypted_api_key")).isNull();
        assertThat(config.get("enabled")).isEqualTo(false);
        assertThat(((Number) config.get("version")).longValue()).isEqualTo(0L);
    }
}
