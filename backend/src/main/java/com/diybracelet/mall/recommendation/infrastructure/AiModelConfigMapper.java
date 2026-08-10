package com.diybracelet.mall.recommendation.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 单例 AI 模型配置的数据访问入口。
 */
@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {

    /** Atomically replaces the singleton when the caller still owns its version. */
    @Update("""
        update ai_model_config
        set provider = #{config.provider},
            base_url = #{config.baseUrl},
            model_name = #{config.modelName},
            encrypted_api_key = #{config.encryptedApiKey},
            enabled = #{config.enabled},
            timeout_seconds = #{config.timeoutSeconds},
            max_output_tokens = #{config.maxOutputTokens},
            recommendation_prompt = #{config.recommendationPrompt},
            preference_prompt = #{config.preferencePrompt},
            soft_preference_enabled = #{config.softPreferenceEnabled},
            plan_count = #{config.planCount},
            version = version + 1,
            updated_at = current_timestamp
        where id = 1
          and version = #{expectedVersion}
        """)
    int updateWithVersion(
        @Param("config") AiModelConfig config,
        @Param("expectedVersion") long expectedVersion
    );
}
