package com.diybracelet.mall.inventory.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.inventory.domain.InventoryLedger;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存流水数据访问入口。
 */
@Mapper
public interface InventoryLedgerMapper extends BaseMapper<InventoryLedger> {
}
