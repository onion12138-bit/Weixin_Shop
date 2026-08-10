package com.diybracelet.mall.recommendation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.diybracelet.mall.support.MySqlIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
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
            select id, provider, base_url, model_name, encrypted_api_key,
                   enabled, recommendation_prompt, preference_prompt,
                   soft_preference_enabled, plan_count, version
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
        assertThat(config.get("recommendation_prompt"))
            .isEqualTo(
                "根据客户的生日画像与期望，从候选珠子中推荐风格有区分的日常搭配。"
                    + "优先参考客户明确提出的材质、颜色和标签偏好，并说明每种材质的选择理由；"
                    + "星座、生肖和文化寓意仅作搭配参考，不作任何效果承诺。"
            );
        assertThat(config.get("preference_prompt")).isEqualTo(
            "优先理解用户明确提出的材质、颜色和标签偏好。逐项查看候选珠子的名称、材质、"
                + "颜色描述和全部标签；若存在匹配候选，尽量在多个方案中体现，但不作为"
                + "强制约束。不得虚构候选列表之外的珠子。"
        );
        assertThat(config.get("soft_preference_enabled")).isEqualTo(true);
        assertThat(((Number) config.get("plan_count")).intValue()).isEqualTo(3);
        assertThat(((Number) config.get("version")).longValue()).isEqualTo(0L);
    }

    @Test
    void shouldRejectASecondAiModelConfiguration() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
            insert into ai_model_config (
                id, provider, base_url, model_name, encrypted_api_key,
                enabled, timeout_seconds, max_output_tokens,
                recommendation_prompt, preference_prompt,
                soft_preference_enabled, plan_count, version
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            2L,
            "DEEPSEEK",
            "https://api.deepseek.com",
            "deepseek-v4-flash",
            null,
            false,
            30,
            2048,
            "生成日常材质搭配",
            "识别颜色和标签偏好",
            true,
            3,
            0L
        )).isInstanceOf(DataAccessException.class)
            .hasMessageContaining("chk_ai_model_singleton");

        Integer rowCount = jdbcTemplate.queryForObject(
            "select count(*) from ai_model_config",
            Integer.class
        );
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    void shouldCreateADeletableRecommendationLogWithoutAnySecretColumn() {
        jdbcTemplate.update("""
            insert into ai_recommendation_log (
                trace_id, status, wish_summary, request_json,
                config_snapshot_json, candidates_json, attempts_json
            ) values (?, ?, ?, ?, ?, ?, ?)
            """,
            "trace-blue-migration",
            "SUCCEEDED",
            "我想要蓝色珠子",
            "{\"wish\":\"我想要蓝色珠子\"}",
            "{\"modelName\":\"deepseek-v4-flash\"}",
            "[{\"skuId\":2030,\"color\":\"深蓝\",\"tags\":[\"蓝色\"]}]",
            "[{\"responseStatus\":200}]"
        );

        Map<String, Object> log = jdbcTemplate.queryForMap("""
            select trace_id, status, wish_summary, request_json,
                   config_snapshot_json, candidates_json, attempts_json
            from ai_recommendation_log
            where trace_id = 'trace-blue-migration'
            """);
        Integer secretColumns = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.columns
            where table_schema = database()
              and table_name = 'ai_recommendation_log'
              and lower(column_name) in (
                'api_key', 'encrypted_api_key', 'authorization', 'authorization_header'
              )
            """, Integer.class);

        assertThat(log.get("status")).isEqualTo("SUCCEEDED");
        assertThat(log.get("wish_summary")).isEqualTo("我想要蓝色珠子");
        assertThat(log.get("candidates_json").toString()).contains("深蓝", "蓝色");
        assertThat(secretColumns).isZero();

        assertThat(jdbcTemplate.update(
            "delete from ai_recommendation_log where trace_id = ?",
            "trace-blue-migration"
        )).isEqualTo(1);
    }

    @ParameterizedTest(name = "invalid plan count {0}")
    @ValueSource(ints = {0, 6})
    void shouldRejectPlanCountOutsideOneToFive(int planCount) {
        assertThatThrownBy(() -> jdbcTemplate.update(
            "update ai_model_config set plan_count = ? where id = 1",
            planCount
        ))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("chk_ai_plan_count");
    }
}
