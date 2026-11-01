package com.diybracelet.mall.customer.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.customer.domain.CustomerCoupon;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import com.diybracelet.mall.customer.domain.CouponGrantBatch;

import java.util.List;


/** 顾客优惠券数据访问，核销恢复使用条件更新避免覆盖并发状态。 */
@Mapper
public interface CustomerCouponMapper extends BaseMapper<CustomerCoupon> {
    /** 原子恢复核销状态；已过期权益保持过期，已撤销权益不复活。 */
    @Update("""
            update customer_coupon
            set status = case when expires_at <= current_timestamp then 'EXPIRED' else 'UNUSED' end,
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
                note, coupon_code, coupon_name, coupon_description, discount_amount,
                minimum_spend, snapshot_source, grant_batch_id
            ) values
            <foreach collection="coupons" item="coupon" separator=",">
                (
                    #{coupon.couponTemplateId},
                    #{coupon.customerIdentityId},
                    #{coupon.status},
                    #{coupon.issuedAt},
                    #{coupon.expiresAt},
                    #{coupon.note}, #{coupon.couponCode}, #{coupon.couponName},
                    #{coupon.couponDescription}, #{coupon.discountAmount},
                    #{coupon.minimumSpend}, #{coupon.snapshotSource}, #{coupon.grantBatchId}
                )
            </foreach>
            </script>
            """)
    int insertBatch(@Param("coupons") List<CustomerCoupon> coupons);
    /** 唯一键锁串行化同一批次，失败事务连占位记录一同回滚。 */
    @Insert("""
            insert into coupon_grant_batch(batch_id, request_hash) values(#{batchId}, #{requestHash})
            on duplicate key update batch_id = #{batchId}
            """)
    int reserveBatch(@Param("batchId") String batchId, @Param("requestHash") String requestHash);

    @Select("""
            select batch_id, request_hash, requested_count, granted_count, skipped_count, coupon_id
            from coupon_grant_batch where batch_id = #{batchId} for update
            """)
    CouponGrantBatch lockBatch(@Param("batchId") String batchId);

    @Update("""
            update coupon_grant_batch set requested_count=#{requested}, granted_count=#{granted},
                skipped_count=#{skipped}, coupon_id=#{couponId} where batch_id=#{batchId}
            """)
    int completeBatch(@Param("batchId") String batchId, @Param("requested") int requested,
            @Param("granted") int granted, @Param("skipped") int skipped, @Param("couponId") Long couponId);
}
