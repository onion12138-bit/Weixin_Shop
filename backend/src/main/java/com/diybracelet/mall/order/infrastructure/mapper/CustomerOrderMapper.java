package com.diybracelet.mall.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.order.domain.CustomerOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

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
     * 查询顾客拥有的单个订单，不泄露其他顾客订单是否存在。
     */
    @Select("""
            select *
            from customer_order
            where id = #{orderId}
              and customer_identity_id = #{customerId}
            """)
    CustomerOrder selectOwnedById(
            @Param("orderId") Long orderId,
            @Param("customerId") Long customerId
    );

    /**
     * 按创建时间倒序查询当前顾客的全部订单。
     */
    @Select("""
            select *
            from customer_order
            where customer_identity_id = #{customerId}
            order by id desc
            """)
    List<CustomerOrder> selectOwnedOrders(
            @Param("customerId") Long customerId
    );

    /**
     * 后台履约操作按主键锁定订单，不附加顾客归属条件。
     */
    @Select("""
            select *
            from customer_order
            where id = #{orderId}
            for update
            """)
    CustomerOrder selectByIdForUpdate(@Param("orderId") Long orderId);

    /** 支付成功后订单直接进入制作中。 */
    @Update("""
            update customer_order
            set status = 'IN_PRODUCTION',
                paid_at = #{paidAt},
                version = version + 1
            where id = #{orderId}
              and status = 'PENDING_PAYMENT'
            """)
    int markPaidAndStartProduction(
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

    /** 制作完成后进入待发货。 */
    @Update("""
            update customer_order
            set status = 'PENDING_SHIPMENT',
                version = version + 1
            where id = #{orderId}
              and status = 'IN_PRODUCTION'
            """)
    int markPendingShipment(@Param("orderId") Long orderId);

    /**
     * 制作中订单完成发货。
     */
    @Update("""
            update customer_order
            set status = 'SHIPPED',
                shipped_at = #{shippedAt},
                version = version + 1
            where id = #{orderId}
              and status = 'PENDING_SHIPMENT'
            """)
    int markShipped(
            @Param("orderId") Long orderId,
            @Param("shippedAt") LocalDateTime shippedAt
    );

    /**
     * 已发货订单标记为完成。
     */
    @Update("""
            update customer_order
            set status = 'COMPLETED',
                completed_at = #{completedAt},
                version = version + 1
            where id = #{orderId}
              and status = 'SHIPPED'
            """)
    int markCompleted(
            @Param("orderId") Long orderId,
            @Param("completedAt") LocalDateTime completedAt
    );

    /** 后台修改尚未发货订单的收货信息。 */
    @Update("""
            update customer_order
            set contact_name = #{contactName},
                contact_phone = #{contactPhone},
                address_detail = #{addressDetail},
                order_note = #{orderNote},
                version = version + 1
            where id = #{orderId}
              and status in (
                  'PENDING_PAYMENT', 'IN_PRODUCTION', 'PENDING_SHIPMENT'
              )
            """)
    int updateAddress(
            @Param("orderId") Long orderId,
            @Param("contactName") String contactName,
            @Param("contactPhone") String contactPhone,
            @Param("addressDetail") String addressDetail,
            @Param("orderNote") String orderNote
    );
}
