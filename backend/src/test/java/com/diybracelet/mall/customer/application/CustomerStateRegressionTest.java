package com.diybracelet.mall.customer.application;

import com.diybracelet.mall.customer.api.dto.CustomerDtos.SaveProfileRequest;
import com.diybracelet.mall.customer.infrastructure.CouponTemplateMapper;
import com.diybracelet.mall.customer.infrastructure.CustomerAddressMapper;
import com.diybracelet.mall.customer.infrastructure.CustomerCouponMapper;
import com.diybracelet.mall.customer.infrastructure.CustomerProfileMapper;
import com.diybracelet.mall.file.infrastructure.AssetFileMapper;
import com.diybracelet.mall.identity.infrastructure.CustomerIdentityMapper;
import com.diybracelet.mall.support.MySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CustomerStateRegressionTest extends MySqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerIdentityMapper identities;
    @Autowired CustomerCouponMapper coupons;
    @Autowired CouponTemplateMapper templates;
    @Autowired CustomerProfileMapper profiles;
    @Autowired CustomerAddressMapper addresses;
    @Autowired AssetFileMapper assets;

    private long customerId;

    @BeforeEach
    void createCustomer() {
        String hash = UUID.randomUUID().toString().replace("-", "").repeat(2);
        jdbc.update("insert into customer_identity (source, external_key_hash, status) values ('GUEST', ?, 'ACTIVE')", hash);
        customerId = jdbc.queryForObject("select id from customer_identity where external_key_hash = ?", Long.class, hash);
    }

    @Test
    void listingAnExpiredSnapshotMustNotOverwriteACompletedCouponRedemption() {
        String code = "RACE-" + UUID.randomUUID();
        jdbc.update("""
                insert into coupon_template (code, name, description, discount_amount, minimum_spend, valid_days, status)
                values (?, '测试券', '并发核销回归', 10, 20, 30, 'ACTIVE')
                """, code);
        long templateId = jdbc.queryForObject("select id from coupon_template where code = ?", Long.class, code);
        jdbc.update("""
                insert into customer_coupon (coupon_template_id, customer_identity_id, status, issued_at, expires_at)
                values (?, ?, 'UNUSED', ?, ?)
                """, templateId, customerId, LocalDateTime.now().minusDays(1), LocalDateTime.now().minusSeconds(1));
        long couponId = jdbc.queryForObject("select id from customer_coupon where customer_identity_id = ?", Long.class, customerId);
        LocalDateTime usedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        // Keep the real database/mapper behavior. Interleave the order's completed
        // redemption immediately after the listing's snapshot is read.
        CustomerCouponMapper interleaved = (CustomerCouponMapper) Proxy.newProxyInstance(
                CustomerCouponMapper.class.getClassLoader(),
                new Class<?>[]{CustomerCouponMapper.class}, (proxy, method, args) -> {
                    Object result = method.invoke(coupons, args);
                    if (method.getName().equals("selectList")) {
                        jdbc.update("update customer_coupon set status = 'USED', used_at = ? where id = ?", usedAt, couponId);
                    }
                    return result;
                });

        new CouponService(() -> customerId, identities, templates, interleaved).listMine();

        assertThat(jdbc.queryForObject("select status from customer_coupon where id = ?", String.class, couponId)).isEqualTo("USED");
        assertThat(jdbc.queryForObject("select used_at from customer_coupon where id = ?", LocalDateTime.class, couponId)).isEqualTo(usedAt);
    }

    @Test
    void clearingAnExistingAvatarMustPersistNull() {
        String key = "test/" + UUID.randomUUID() + ".png";
        jdbc.update("""
                insert into asset_file (storage_key, original_name, content_type, size_bytes, public_url, status)
                values (?, 'avatar.png', 'image/png', 1, ?, 'ACTIVE')
                """, key, "/api/public/files/" + key);
        long assetId = jdbc.queryForObject("select id from asset_file where storage_key = ?", Long.class, key);
        jdbc.update("insert into customer_profile (customer_identity_id, nickname, avatar_asset_id) values (?, '顾客', ?)", customerId, assetId);
        CustomerAccountService service = new CustomerAccountService(
                () -> customerId, identities, profiles, addresses, assets);

        var profile = service.saveProfile(new SaveProfileRequest("顾客", null));

        assertThat(profile.avatarAssetId()).isNull();
        assertThat(jdbc.queryForObject("select avatar_asset_id from customer_profile where customer_identity_id = ?", Long.class, customerId)).isNull();
    }
}
