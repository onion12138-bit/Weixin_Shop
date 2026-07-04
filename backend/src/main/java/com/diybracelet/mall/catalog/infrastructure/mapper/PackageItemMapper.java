package com.diybracelet.mall.catalog.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.catalog.domain.PackageItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 套餐散珠明细的数据访问入口。
 */
@Mapper
public interface PackageItemMapper extends BaseMapper<PackageItem> {
}
