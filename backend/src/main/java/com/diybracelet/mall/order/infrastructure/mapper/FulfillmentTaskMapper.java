package com.diybracelet.mall.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.order.domain.FulfillmentTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 履约任务数据访问入口。
 */
@Mapper
public interface FulfillmentTaskMapper extends BaseMapper<FulfillmentTask> {
}
