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
}
