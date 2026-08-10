package com.diybracelet.mall.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.diybracelet.mall.support.MySqlIntegrationTest;
import java.math.BigDecimal;
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
     * 空库启动后必须包含基础珠子、绳类和其他饰品分类。
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

        Integer cordCount = jdbcTemplate.queryForObject(
            "select count(*) from category where slug = 'cord' and product_type = 'CORD'",
            Integer.class
        );
        Integer accessoryCount = jdbcTemplate.queryForObject(
            "select count(*) from category where slug = 'accessory' and product_type = 'ACCESSORY'",
            Integer.class
        );

        assertThat(categoryCount).isGreaterThanOrEqualTo(4);
        assertThat(beadCount).isGreaterThanOrEqualTo(4);
        assertThat(cordCount).isEqualTo(1);
        assertThat(accessoryCount).isEqualTo(1);
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
        assertThat(config.get("bead_shadow_opacity"))
                .isEqualTo(new BigDecimal("0.100"));
        assertThat(config.get("config_version")).isEqualTo(2L);
    }

    @Test
    void shouldCreatePackageHomeRecommendationFlag() {
        Integer recommendationColumns = jdbcTemplate.queryForObject("""
            select count(*)
              from information_schema.columns
             where table_schema = database()
               and table_name = 'product_package'
               and column_name = 'recommended'
            """, Integer.class);
        Boolean seededRecommendation = jdbcTemplate.queryForObject(
            "select recommended from product_package where id = 3001",
            Boolean.class
        );

        assertThat(recommendationColumns).isEqualTo(1);
        assertThat(seededRecommendation).isFalse();
    }

    @Test
    void shouldCreateCustomerAccountAndCouponTables() {
        Integer tableCount = jdbcTemplate.queryForObject("""
            select count(*)
              from information_schema.tables
             where table_schema = database()
               and table_name in (
                 'customer_profile', 'customer_address',
                 'coupon_template', 'customer_coupon'
               )
            """, Integer.class);
        assertThat(tableCount).isEqualTo(4);
    }
}
