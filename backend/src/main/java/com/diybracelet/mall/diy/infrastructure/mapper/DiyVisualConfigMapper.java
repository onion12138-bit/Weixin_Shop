package com.diybracelet.mall.diy.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.diy.domain.DiyVisualConfig;
import org.apache.ibatis.annotations.Mapper;

/** DIY 固定视图渲染配置的数据访问入口。 */
@Mapper
public interface DiyVisualConfigMapper extends BaseMapper<DiyVisualConfig> {
}
