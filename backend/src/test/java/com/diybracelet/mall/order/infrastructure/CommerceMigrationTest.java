package com.diybracelet.mall.order.infrastructure;

import com.diybracelet.mall.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证库存、订单、模拟支付和履约阶段的数据库结构能在真实 MySQL 8 上迁移。
 *
 * <p>本阶段涉及库存数量流转、订单状态机和支付幂等记录，必须使用真实 MySQL
 * 的外键、check 约束、decimal 精度和 JSON 字段验证，不能使用 H2 替代。</p>
 */
class CommerceMigrationTest extends MySqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateCommerceTablesAndSeedInventory() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = database()
                  and table_name in (
                    'inventory_balance',
                    'inventory_ledger',
                    'customer_order',
                    'order_item',
                    'payment_record',
                    'fulfillment_task'
                  )
                """, Integer.class);
        Integer stockedSkuCount = jdbcTemplate.queryForObject("""
                select count(*)
                from inventory_balance
                where bead_sku_id in (2001, 2002, 2003, 2004)
                  and available_qty >= 100
                  and reserved_qty = 0
                  and sold_qty = 0
                """, Integer.class);

        assertThat(tableCount).isEqualTo(6);
        assertThat(stockedSkuCount).isEqualTo(4);
    }
}
