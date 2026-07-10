package com.diybracelet.mall.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.order.domain.CustomerOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 顾客订单数据访问入口。
 */
@Mapper
public interface CustomerOrderMapper extends BaseMapper<CustomerOrder> {

    /**
     * 按订单和顾客锁定订单行，既校验资源归属，也串行化支付与取消操作。
     */
    @Select("""
            select *
            from customer_order
            where id = #{orderId}
              and customer_identity_id = #{customerId}
            for update
            """)
    CustomerOrder selectOwnedByIdForUpdate(
            @Param("orderId") Long orderId,
            @Param("customerId") Long customerId
    );

    /**
     * 把待支付订单标记为已支付。
     */
    @Update("""
            update customer_order
            set status = 'PAID',
                paid_at = #{paidAt},
                version = version + 1
            where id = #{orderId}
              and status = 'PENDING_PAYMENT'
            """)
    int markPaid(
            @Param("orderId") Long orderId,
            @Param("paidAt") LocalDateTime paidAt
    );

    /**
     * 把待支付订单标记为已取消。
     */
    @Update("""
            update customer_order
            set status = 'CANCELLED',
                cancelled_at = #{cancelledAt},
                version = version + 1
            where id = #{orderId}
              and status = 'PENDING_PAYMENT'
            """)
    int markCancelled(
            @Param("orderId") Long orderId,
            @Param("cancelledAt") LocalDateTime cancelledAt
    );
}
