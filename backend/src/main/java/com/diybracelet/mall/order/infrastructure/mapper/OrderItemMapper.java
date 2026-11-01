package com.diybracelet.mall.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.order.domain.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单明细数据访问入口。
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Select("""
            select *
            from order_item
            where order_id = #{orderId}
            order by sort_order asc, id asc
            """)
    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);

    /** 按顾客一次读取列表明细，避免每个订单各发起一次查询。 */
    @Select("""
            select item.* from order_item item
            join customer_order orders on orders.id = item.order_id
            where orders.customer_identity_id = #{customerId}
            order by item.order_id, item.sort_order asc, item.id asc
            """)
    List<OrderItem> selectOwnedByCustomer(@Param("customerId") Long customerId);
}
