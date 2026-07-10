package com.diybracelet.mall.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.order.domain.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付记录数据访问入口。
 */
@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
}
