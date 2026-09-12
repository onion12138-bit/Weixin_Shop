package com.diybracelet.mall.customer.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.customer.domain.CustomerCoupon;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CustomerCouponMapper extends BaseMapper<CustomerCoupon> {
    /** Restore atomically without writing a stale entity or retaining used_at. */
    @Update("""
            update customer_coupon
            set status = 'UNUSED',
                used_at = null
            where id = #{couponId}
              and customer_identity_id = #{customerId}
              and status = 'USED'
            """)
    int restoreUsedCoupon(
            @Param("couponId") Long couponId,
            @Param("customerId") Long customerId
    );

    @Insert("""
            <script>
            insert into customer_coupon (
                coupon_template_id,
                customer_identity_id,
                status,
                issued_at,
                expires_at,
                note
            ) values
            <foreach collection="coupons" item="coupon" separator=",">
                (
                    #{coupon.couponTemplateId},
                    #{coupon.customerIdentityId},
                    #{coupon.status},
                    #{coupon.issuedAt},
                    #{coupon.expiresAt},
                    #{coupon.note}
                )
            </foreach>
            </script>
            """)
    int insertBatch(@Param("coupons") List<CustomerCoupon> coupons);
}
