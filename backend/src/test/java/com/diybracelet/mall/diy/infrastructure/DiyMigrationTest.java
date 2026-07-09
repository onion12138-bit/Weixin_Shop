package com.diybracelet.mall.diy.infrastructure;

import com.diybracelet.mall.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 DIY 阶段新增的数据库结构和默认配置可以在真实 MySQL 8 上正常迁移。
 *
 * <p>这个测试特意不使用 H2：本阶段需要 JSON 字段、外键、check 约束和 decimal
 * 精度保持与生产环境一致，只有真实 MySQL 才能尽早暴露迁移脚本问题。</p>
 */
class DiyMigrationTest extends MySqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateDiySchemaAndSeedUsableDefaults() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = database()
                  and table_name in (
                    'customer_identity',
                    'diy_rule_config',
                    'craft_fee_rule',
                    'cord_option',
                    'diy_scheme'
                  )
                """, Integer.class);
        Integer maxBeads = jdbcTemplate.queryForObject(
                "select max_bead_count from diy_rule_config where id = 1",
                Integer.class
        );
        Integer enabledCords = jdbcTemplate.queryForObject(
                "select count(*) from cord_option where status = 'ENABLED'",
                Integer.class
        );

        assertThat(tableCount).isEqualTo(5);
        assertThat(maxBeads).isEqualTo(60);
        assertThat(enabledCords).isEqualTo(3);
    }
}
