package com.diybracelet.mall.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.order.domain.OrderOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 订单操作记录访问入口。 */
@Mapper
public interface OrderOperationLogMapper extends BaseMapper<OrderOperationLog> {
    @Select("""
            select *
            from order_operation_log
            where order_id = #{orderId}
            order by occurred_at asc, id asc
            """)
    List<OrderOperationLog> selectByOrderId(@Param("orderId") Long orderId);
}
