package com.diybracelet.mall.commerce.application;

import com.diybracelet.mall.commerce.api.dto.CommerceRuleView;
import com.diybracelet.mall.commerce.api.dto.SaveCommerceRuleRequest;
import com.diybracelet.mall.commerce.domain.CommerceRuleConfig;
import com.diybracelet.mall.commerce.infrastructure.CommerceRuleConfigMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** 集中读取、保存并执行全店交易规则。 */
@Service
public class CommerceRuleService {

    private static final long SINGLETON_ID = 1L;
    private final CommerceRuleConfigMapper mapper;

    public CommerceRuleService(CommerceRuleConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public CommerceRuleView get() {
        return toView(requireConfig());
    }

    @Transactional
    public CommerceRuleView save(SaveCommerceRuleRequest request) {
        CommerceRuleConfig config = mapper.selectByIdForUpdate(SINGLETON_ID);
        if (config == null) throw missingConfig();
        config.setMinimumOrderAmount(request.minimumOrderAmount());
        config.setFreeShippingThreshold(request.freeShippingThreshold());
        config.setBaseShippingFee(request.baseShippingFee());
        config.setConfigVersion(config.getConfigVersion() + 1);
        mapper.updateById(config);
        return toView(config);
    }

    @Transactional(readOnly = true)
    public CheckoutRule checkoutRule() {
        CommerceRuleConfig config = requireConfig();
        return new CheckoutRule(
                config.getMinimumOrderAmount(),
                config.getFreeShippingThreshold(),
                config.getBaseShippingFee()
        );
    }

    public record CheckoutRule(
            BigDecimal minimumOrderAmount,
            BigDecimal freeShippingThreshold,
            BigDecimal baseShippingFee
    ) {
        public BigDecimal shippingFee(BigDecimal discountedProductAmount) {
            return discountedProductAmount.compareTo(freeShippingThreshold) >= 0
                    ? BigDecimal.ZERO : baseShippingFee;
        }
    }

    private CommerceRuleConfig requireConfig() {
        CommerceRuleConfig config = mapper.selectById(SINGLETON_ID);
        if (config == null) throw missingConfig();
        return config;
    }

    private BusinessException missingConfig() {
        return new BusinessException(
                ErrorCode.COMMON_INTERNAL_ERROR,
                "交易规则配置缺失"
        );
    }

    private CommerceRuleView toView(CommerceRuleConfig config) {
        return new CommerceRuleView(
                config.getMinimumOrderAmount(),
                config.getFreeShippingThreshold(),
                config.getBaseShippingFee(),
                config.getConfigVersion()
        );
    }
}
