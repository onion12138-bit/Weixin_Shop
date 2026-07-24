package com.diybracelet.mall.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.diybracelet.mall.support.MySqlIntegrationTest;
import java.util.Map;
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

    @Test
    void shouldCreateManualVisualSchemaAndDefaultConfig() {
        Integer versionColumns = jdbcTemplate.queryForObject("""
            select count(*)
              from information_schema.columns
             where table_schema = database()
               and table_name = 'bead_visual_version'
               and column_name in (
                 'bead_sku_id', 'version_no', 'source_type', 'status',
                 'tray_asset_id', 'canvas_asset_id', 'created_by',
                 'published_by', 'published_at'
               )
            """, Integer.class);
        assertThat(versionColumns).isEqualTo(9);

        Integer pointerColumns = jdbcTemplate.queryForObject("""
            select count(*)
              from information_schema.columns
             where table_schema = database()
               and table_name = 'bead_sku'
               and column_name = 'published_visual_version_id'
            """, Integer.class);
        assertThat(pointerColumns).isEqualTo(1);

        Map<String, Object> config = jdbcTemplate.queryForMap(
            "select * from diy_visual_config where id = 1"
        );
        assertThat(config.get("canvas_background_color")).isEqualTo("#fffdfb");
        assertThat(config.get("config_version")).isEqualTo(1L);
    }
}
