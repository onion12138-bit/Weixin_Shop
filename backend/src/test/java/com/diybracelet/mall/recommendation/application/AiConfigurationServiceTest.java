package com.diybracelet.mall.recommendation.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.diybracelet.mall.recommendation.api.dto.SaveAiConfigurationRequest;
import com.diybracelet.mall.recommendation.domain.AiModelConfig;
import com.diybracelet.mall.recommendation.domain.AiModelGateway;
import com.diybracelet.mall.recommendation.infrastructure.AiModelConfigMapper;
import com.diybracelet.mall.recommendation.infrastructure.AiSecretCipher;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

class AiConfigurationServiceTest {

    @Test
    void shouldReportAStableErrorWhenTheServerEncryptionKeyIsMissing() {
        AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
        AiSecretCipher cipher = new AiSecretCipher("");
        AiModelGateway gateway = mock(AiModelGateway.class);
        AiModelConfig stored = new AiModelConfig();
        stored.setId(1L);
        stored.setVersion(2L);
        when(mapper.selectById(1L)).thenReturn(stored);
        AiConfigurationService service = new AiConfigurationService(
            mapper,
            cipher,
            gateway
        );

        assertThatThrownBy(() -> service.save(new SaveAiConfigurationRequest(
            "https://api.deepseek.com",
            "deepseek-v4-flash",
            "provider-secret",
            true,
            30,
            2048,
            "生成日常材质搭配",
            "识别颜色和标签偏好",
            true,
            3,
            2
        )))
            .isInstanceOf(BusinessException.class)
            .hasMessage("AI 加密配置未就绪，请重新启动管理后台")
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.AI_SERVER_MISCONFIGURED);
    }
}
