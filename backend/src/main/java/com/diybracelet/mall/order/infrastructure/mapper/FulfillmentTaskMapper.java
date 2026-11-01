package com.diybracelet.mall.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.order.domain.FulfillmentTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 履约任务数据访问入口。
 */
@Mapper
public interface FulfillmentTaskMapper extends BaseMapper<FulfillmentTask> {

    /**
     * 查询订单对应的唯一履约任务。
     */
    @Select("""
            select *
            from fulfillment_task
            where order_id = #{orderId}
            """)
    FulfillmentTask selectByOrderId(@Param("orderId") Long orderId);

    /** 批量获取顾客订单的物流状态，归属条件在数据库侧统一校验。 */
    @Select("""
            select task.* from fulfillment_task task
            join customer_order orders on orders.id = task.order_id
            where orders.customer_identity_id = #{customerId}
            """)
    List<FulfillmentTask> selectOwnedByCustomer(@Param("customerId") Long customerId);

    /**
     * 锁定履约任务，保证多个后台操作不能并发跳转状态。
     */
    @Select("""
            select *
            from fulfillment_task
            where order_id = #{orderId}
            for update
            """)
    FulfillmentTask selectByOrderIdForUpdate(
            @Param("orderId") Long orderId
    );

    /** 制作完成后任务进入待发货。 */
    @Update("""
            update fulfillment_task
            set status = 'PENDING_SHIPMENT',
                production_completed_at = #{completedAt}
            where order_id = #{orderId}
              and status = 'IN_PRODUCTION'
            """)
    int completeProduction(
            @Param("orderId") Long orderId,
            @Param("completedAt") LocalDateTime completedAt
    );

    /**
     * 制作中任务发货并保存模拟物流信息。
     */
    @Update("""
            update fulfillment_task
            set status = 'SHIPPED',
                logistics_company = #{logisticsCompany},
                tracking_no = #{trackingNo},
                shipped_at = #{shippedAt}
            where order_id = #{orderId}
              and status = 'PENDING_SHIPMENT'
            """)
    int ship(
            @Param("orderId") Long orderId,
            @Param("logisticsCompany") String logisticsCompany,
            @Param("trackingNo") String trackingNo,
            @Param("shippedAt") LocalDateTime shippedAt
    );

    /**
     * 已发货任务完成履约。
     */
    @Update("""
            update fulfillment_task
            set status = 'COMPLETED',
                completed_at = #{completedAt}
            where order_id = #{orderId}
              and status = 'SHIPPED'
            """)
    int complete(
            @Param("orderId") Long orderId,
            @Param("completedAt") LocalDateTime completedAt
    );
}
