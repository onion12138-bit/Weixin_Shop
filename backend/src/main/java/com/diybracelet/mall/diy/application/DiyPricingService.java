package com.diybracelet.mall.diy.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.diybracelet.mall.catalog.domain.BeadSku;
import com.diybracelet.mall.catalog.infrastructure.mapper.BeadSkuMapper;
import com.diybracelet.mall.diy.domain.CordOption;
import com.diybracelet.mall.diy.domain.CraftFeeRule;
import com.diybracelet.mall.diy.domain.DiyDesignCommand;
import com.diybracelet.mall.diy.domain.DiyEvaluation;
import com.diybracelet.mall.diy.domain.DiyRuleConfig;
import com.diybracelet.mall.diy.domain.DiyValidationIssue;
import com.diybracelet.mall.diy.domain.OptionStatus;
import com.diybracelet.mall.diy.domain.SizeStatus;
import com.diybracelet.mall.diy.infrastructure.mapper.CordOptionMapper;
import com.diybracelet.mall.diy.infrastructure.mapper.CraftFeeRuleMapper;
import com.diybracelet.mall.diy.infrastructure.mapper.DiyRuleConfigMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DIY 设计尺寸评估与权威计价服务。
 *
 * <p>本服务是价格唯一可信来源。小程序可以做实时预估，但保存方案或进入订单前必须调用
 * 本服务读取当前 SKU、尺寸规则、工艺费和绳材配置重新计算。</p>
 */
@Service
public class DiyPricingService {

    private static final long SINGLETON_RULE_ID = 1L;
    private static final BigDecimal ZERO_MONEY = money(BigDecimal.ZERO);

    private final DiyRuleConfigMapper ruleConfigMapper;
    private final CraftFeeRuleMapper craftFeeRuleMapper;
    private final CordOptionMapper cordOptionMapper;
    private final BeadSkuMapper beadSkuMapper;

    public DiyPricingService(
            DiyRuleConfigMapper ruleConfigMapper,
            CraftFeeRuleMapper craftFeeRuleMapper,
            CordOptionMapper cordOptionMapper,
            BeadSkuMapper beadSkuMapper
    ) {
        this.ruleConfigMapper = ruleConfigMapper;
        this.craftFeeRuleMapper = craftFeeRuleMapper;
        this.cordOptionMapper = cordOptionMapper;
        this.beadSkuMapper = beadSkuMapper;
    }

    /**
     * 评估当前 DIY 设计是否可报价，并在可报价时返回权威价格。
     * 尺寸偏差只作为搭配建议返回，不参与报价可用性判断。
     *
     * @param command 设计命令
     * @return 评估结果
     */
    public DiyEvaluation evaluate(DiyDesignCommand command) {
        DiyRuleConfig config = requireRuleConfig();
        LinkedHashMap<Long, Integer> quantities =
                deriveQuantities(command.sequence());
        List<DiyValidationIssue> issues = new ArrayList<>();

        if (quantities.isEmpty()) {
            issues.add(issue("EMPTY_SEQUENCE", "请至少选择一颗珠子", null));
        }
        if (command.wristCircumferenceMm().compareTo(config.getMinWristMm()) < 0
                || command.wristCircumferenceMm()
                .compareTo(config.getMaxWristMm()) > 0) {
            issues.add(issue(
                    "WRIST_OUT_OF_RANGE",
                    "腕围超出可配置范围",
                    null
            ));
        }
        if (command.sequence().size() > config.getMaxBeadCount()) {
            issues.add(issue(
                    "BEAD_COUNT_EXCEEDED",
                    "珠子数量超过当前最大限制",
                    null
            ));
        }

        Map<Long, BeadSku> beadsById = loadSellableBeads(quantities);
        for (Long skuId : quantities.keySet()) {
            if (!beadsById.containsKey(skuId)) {
                issues.add(issue(
                        "SKU_UNAVAILABLE",
                        "散珠不存在或已下架",
                        skuId
                ));
            }
        }

        CordOption cordOption = cordOptionMapper.selectById(
                command.cordOptionId()
        );
        if (cordOption == null
                || cordOption.getStatus() != OptionStatus.ENABLED) {
            issues.add(issue("CORD_UNAVAILABLE", "绳材不存在或已停用", null));
        }

        BigDecimal currentLength = currentLength(quantities, beadsById);
        BigDecimal targetLength = length(
                command.wristCircumferenceMm()
                        .add(command.fitType().allowanceFrom(config))
        );
        BigDecimal deviation = length(currentLength.subtract(targetLength));
        SizeStatus sizeStatus = sizeStatus(deviation, config.getToleranceMm());

        CraftFeeRule craftFee = matchingCraftFee(command.sequence().size());
        if (craftFee == null && !quantities.isEmpty()) {
            issues.add(issue(
                    "CRAFT_FEE_NOT_CONFIGURED",
                    "当前珠数没有匹配的工艺费",
                    null
            ));
        }

        if (!issues.isEmpty()) {
            return new DiyEvaluation(
                    false,
                    List.copyOf(issues),
                    Collections.unmodifiableMap(
                            new LinkedHashMap<>(quantities)
                    ),
                    currentLength,
                    targetLength,
                    deviation,
                    sizeStatus,
                    ZERO_MONEY,
                    ZERO_MONEY,
                    ZERO_MONEY,
                    ZERO_MONEY,
                    null
            );
        }

        BigDecimal beadAmount = beadAmount(quantities, beadsById);
        BigDecimal craftFeeAmount = money(craftFee.getFee());
        BigDecimal cordFee = money(cordOption.getExtraFee());
        BigDecimal totalAmount = money(
                beadAmount.add(craftFeeAmount).add(cordFee)
        );
        String quoteFingerprint = fingerprint(
                config,
                cordOption,
                craftFee,
                quantities,
                beadsById
        );
        return new DiyEvaluation(
                true,
                List.of(),
                Collections.unmodifiableMap(new LinkedHashMap<>(quantities)),
                currentLength,
                targetLength,
                deviation,
                sizeStatus,
                beadAmount,
                craftFeeAmount,
                cordFee,
                totalAmount,
                quoteFingerprint
        );
    }

    private DiyRuleConfig requireRuleConfig() {
        DiyRuleConfig config = ruleConfigMapper.selectById(SINGLETON_RULE_ID);
        if (config == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_INTERNAL_ERROR,
                    "DIY 尺寸规则缺失"
            );
        }
        return config;
    }

    private LinkedHashMap<Long, Integer> deriveQuantities(List<Long> sequence) {
        LinkedHashMap<Long, Integer> quantities = new LinkedHashMap<>();
        for (Long skuId : sequence) {
            quantities.merge(skuId, 1, Integer::sum);
        }
        return quantities;
    }

    private Map<Long, BeadSku> loadSellableBeads(
            Map<Long, Integer> quantities
    ) {
        if (quantities.isEmpty()) {
            return Map.of();
        }
        return beadSkuMapper
                .selectSellableForDiy(new ArrayList<>(quantities.keySet()))
                .stream()
                .collect(Collectors.toMap(
                        BeadSku::getId,
                        Function.identity()
                ));
    }

    private BigDecimal currentLength(
            Map<Long, Integer> quantities,
            Map<Long, BeadSku> beadsById
    ) {
        BigDecimal length = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            BeadSku bead = beadsById.get(entry.getKey());
            if (bead != null) {
                length = length.add(
                        bead.getDiameterMm()
                                .multiply(BigDecimal.valueOf(entry.getValue()))
                );
            }
        }
        return length(length);
    }

    private SizeStatus sizeStatus(
            BigDecimal deviation,
            BigDecimal tolerance
    ) {
        if (deviation.abs().compareTo(tolerance) <= 0) {
            return SizeStatus.FIT;
        }
        return deviation.signum() < 0
                ? SizeStatus.TOO_SHORT
                : SizeStatus.TOO_LONG;
    }

    private CraftFeeRule matchingCraftFee(int beadCount) {
        return craftFeeRuleMapper.selectList(
                        Wrappers.lambdaQuery(CraftFeeRule.class)
                                .eq(CraftFeeRule::getStatus, OptionStatus.ENABLED)
                                .le(CraftFeeRule::getMinBeadCount, beadCount)
                                .ge(CraftFeeRule::getMaxBeadCount, beadCount)
                                .orderByAsc(CraftFeeRule::getMinBeadCount)
                                .last("limit 1")
                )
                .stream()
                .findFirst()
                .orElse(null);
    }

    private BigDecimal beadAmount(
            Map<Long, Integer> quantities,
            Map<Long, BeadSku> beadsById
    ) {
        return money(quantities.entrySet().stream()
                .map(entry -> beadsById.get(entry.getKey())
                        .getSalePrice()
                        .multiply(BigDecimal.valueOf(entry.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private String fingerprint(
            DiyRuleConfig config,
            CordOption cordOption,
            CraftFeeRule craftFee,
            Map<Long, Integer> quantities,
            Map<Long, BeadSku> beadsById
    ) {
        String canonical = "config=" + config.getConfigVersion()
                + "|cord=" + cordOption.getId()
                + ":"
                + cordOption.getOptionVersion()
                + "|craft=" + craftFee.getId()
                + ":"
                + craftFee.getRuleVersion()
                + quantities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "|sku="
                        + entry.getKey()
                        + ":"
                        + entry.getValue()
                        + ":"
                        + money(beadsById.get(entry.getKey()).getSalePrice())
                        .toPlainString())
                .collect(Collectors.joining());
        return sha256Hex(canonical);
    }

    private String sha256Hex(String canonical) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    messageDigest.digest(
                            canonical.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private DiyValidationIssue issue(
            String code,
            String message,
            Long skuId
    ) {
        return new DiyValidationIssue(code, message, skuId);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal length(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
