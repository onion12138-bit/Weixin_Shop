package com.diybracelet.mall.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.order.domain.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 支付记录数据访问入口。
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
    @Select("""
            select * from payment_record
            where order_id = #{orderId}
            limit 1
            """)
    PaymentRecord selectByOrderId(@Param("orderId") Long orderId);
}
