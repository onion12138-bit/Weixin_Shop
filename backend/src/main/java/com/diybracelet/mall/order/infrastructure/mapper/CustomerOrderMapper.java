package com.diybracelet.mall.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.order.domain.CustomerOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 顾客订单数据访问入口。
 */
@Mapper
public interface CustomerOrderMapper extends BaseMapper<CustomerOrder> {
}
