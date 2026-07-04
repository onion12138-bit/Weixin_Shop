package com.diybracelet.mall.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.diybracelet.mall.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 验证 Flyway 能够在一个全新的 MySQL 8 实例中建立商品目录结构和演示数据。
 *
 * <p>该测试直接查询数据库表，避免应用层代码掩盖迁移脚本缺表、字段拼写错误或
 * 种子数据未执行等问题。</p>
 */
class CatalogMigrationTest extends MySqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 空库启动后必须至少存在两个分类和四个已上架珠子。
     */
    @Test
    void shouldCreateCatalogTablesAndSeedVisibleProducts() {
        Integer categoryCount = jdbcTemplate.queryForObject(
            "select count(*) from category",
            Integer.class
        );
        Integer beadCount = jdbcTemplate.queryForObject(
            "select count(*) from bead_sku where status = 'ON_SALE'",
            Integer.class
        );

        assertThat(categoryCount).isGreaterThanOrEqualTo(2);
        assertThat(beadCount).isGreaterThanOrEqualTo(4);
    }
}
