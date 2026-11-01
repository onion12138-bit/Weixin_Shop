package com.diybracelet.mall.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** 从有历史订单和优惠券的 V17 数据库验证升级，避免只验证空库建表。 */
class ReliableOperationsMigrationTest {
    @Test
    void upgradePreservesOldCouponRulesAndGivesPendingOrdersGracePeriod() {
        try (var mysql = new MySQLContainer<>("mysql:8.0")) {
            mysql.start();
            var source = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            Flyway.configure().dataSource(source).target("17").load().migrate();
            var jdbc = new JdbcTemplate(source);
            jdbc.update("""
                    insert into customer_identity(id,source,external_key_hash,status)
                    values(900001,'GUEST',repeat('a',64),'ACTIVE')
                    """);
            jdbc.update("""
                    insert into coupon_template(id,code,name,description,discount_amount,minimum_spend,valid_days,status)
                    values(900001,'LEGACY','迁移时规则','停发旧券',10,20,30,'INACTIVE')
                    """);
            jdbc.update("""
                    insert into customer_coupon(id,coupon_template_id,customer_identity_id,status,expires_at)
                    values(900001,900001,900001,'UNUSED',date_add(current_timestamp,interval 7 day))
                    """);
            jdbc.update("""
                    insert into diy_scheme(id,customer_identity_id,name,wrist_circumference_mm,fit_type,
                    cord_option_id,sequence_json,bead_count,catalog_snapshot_json,quote_status,status,version)
                    values(900001,900001,'旧方案',160,'STANDARD',6001,'[2001]',1,'[]','NOT_QUOTED','ACTIVE',0)
                    """);
            for (String status : new String[]{"PENDING_PAYMENT", "IN_PRODUCTION"}) {
                jdbc.update("""
                        insert into customer_order(order_no,customer_identity_id,diy_scheme_id,status,
                        contact_name,contact_phone,address_detail,scheme_snapshot_json,
                        bead_amount,craft_fee,cord_fee,total_amount,version,created_at)
                        values(?,900001,900001,?,'顾客','13800000000','测试地址','{}',20,10,0,30,0,
                        date_sub(current_timestamp,interval 30 day))
                        """, "LEGACY-" + status, status);
            }
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForMap("""
                    select coupon_code,coupon_name,snapshot_source,grant_batch_id from customer_coupon where id=900001
                    """))
                    .containsEntry("coupon_code", "LEGACY")
                    .containsEntry("coupon_name", "迁移时规则")
                    .containsEntry("snapshot_source", "MIGRATED_CURRENT_TEMPLATE")
                    .containsEntry("grant_batch_id", null);
            jdbc.update("update coupon_template set discount_amount=1,minimum_spend=999 where id=900001");
            assertThat(jdbc.queryForObject("select discount_amount from customer_coupon where id=900001", java.math.BigDecimal.class))
                    .isEqualByComparingTo("10");
            assertThat(jdbc.queryForObject("""
                    select timestampdiff(second,current_timestamp(3),expires_at)
                    from customer_order where status='PENDING_PAYMENT'
                    """, Long.class)).isBetween(1700L, 1800L);
            assertThat(jdbc.queryForObject("""
                    select count(*) from customer_order where status='IN_PRODUCTION' and expires_at is null
                    """, Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from customer_order where checkout_token is not null", Integer.class)).isZero();
        }
    }
}
