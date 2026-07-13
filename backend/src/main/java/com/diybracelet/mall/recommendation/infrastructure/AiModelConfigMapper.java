package com.diybracelet.mall.recommendation.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 单例 AI 模型配置的数据访问入口。
 */
@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {
}
