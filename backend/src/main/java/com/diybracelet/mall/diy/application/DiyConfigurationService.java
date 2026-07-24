package com.diybracelet.mall.diy.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.diybracelet.mall.diy.api.dto.AdminDiyConfigurationView;
import com.diybracelet.mall.diy.api.dto.DiyConfigurationView;
import com.diybracelet.mall.diy.api.dto.SaveCordOptionRequest;
import com.diybracelet.mall.diy.api.dto.SaveCraftFeeRulesRequest;
import com.diybracelet.mall.diy.api.dto.SaveDiyRuleConfigRequest;
import com.diybracelet.mall.diy.api.dto.SaveDiyVisualConfigRequest;
import com.diybracelet.mall.diy.domain.CordOption;
import com.diybracelet.mall.diy.domain.CraftFeeRule;
import com.diybracelet.mall.diy.domain.DiyRuleConfig;
import com.diybracelet.mall.diy.domain.DiyVisualConfig;
import com.diybracelet.mall.diy.domain.OptionStatus;
import com.diybracelet.mall.diy.infrastructure.mapper.CordOptionMapper;
import com.diybracelet.mall.diy.infrastructure.mapper.CraftFeeRuleMapper;
import com.diybracelet.mall.diy.infrastructure.mapper.DiyRuleConfigMapper;
import com.diybracelet.mall.diy.infrastructure.mapper.DiyVisualConfigMapper;
import com.diybracelet.mall.shared.error.BusinessException;
import com.diybracelet.mall.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * DIY 规则、工艺费和绳材配置服务。
 *
 * <p>Controller 只负责 HTTP 契约和参数校验；区间完整性、版本递增和小程序/管理端视图裁剪
 * 都在本服务集中处理，避免报价服务读取到不完整配置。</p>
 */
@Service
public class DiyConfigurationService {

    private static final long SINGLETON_RULE_ID = 1L;
    private static final long SINGLETON_VISUAL_CONFIG_ID = 1L;

    private final DiyRuleConfigMapper ruleConfigMapper;
    private final CraftFeeRuleMapper craftFeeRuleMapper;
    private final CordOptionMapper cordOptionMapper;
    private final DiyVisualConfigMapper visualConfigMapper;

    public DiyConfigurationService(
            DiyRuleConfigMapper ruleConfigMapper,
            CraftFeeRuleMapper craftFeeRuleMapper,
            CordOptionMapper cordOptionMapper,
            DiyVisualConfigMapper visualConfigMapper
    ) {
        this.ruleConfigMapper = ruleConfigMapper;
        this.craftFeeRuleMapper = craftFeeRuleMapper;
        this.cordOptionMapper = cordOptionMapper;
        this.visualConfigMapper = visualConfigMapper;
    }

    /**
     * 获取小程序公开配置。
     *
     * @return 只包含启用项的配置视图
     */
    public DiyConfigurationView getPublicConfiguration() {
        DiyRuleConfig rule = requireRuleConfig();
        List<CraftFeeRule> craftFeeRules = enabledCraftFeeRules();
        List<CordOption> cordOptions = enabledCordOptions();
        return toPublicView(rule, craftFeeRules, cordOptions, requireVisualConfig());
    }

    /**
     * 获取管理后台配置。
     *
     * @return 包含全部绳材状态的管理视图
     */
    public AdminDiyConfigurationView getAdminConfiguration() {
        DiyRuleConfig rule = requireRuleConfig();
        List<CraftFeeRule> craftFeeRules = enabledCraftFeeRules();
        List<CordOption> cordOptions = allCordOptions();
        return toAdminView(rule, craftFeeRules, cordOptions, requireVisualConfig());
    }

    /**
     * 保存 DIY 尺寸规则并递增配置版本。
     *
     * @param request 后台提交的新规则
     * @return 更新后的公开配置
     */
    @Transactional
    public DiyConfigurationView saveRule(SaveDiyRuleConfigRequest request) {
        if (request.minWristMm().compareTo(request.maxWristMm()) >= 0) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "最小腕围必须小于最大腕围"
            );
        }
        DiyRuleConfig current = requireRuleConfig();
        current.setMinWristMm(request.minWristMm());
        current.setMaxWristMm(request.maxWristMm());
        current.setSnugAllowanceMm(request.snugAllowanceMm());
        current.setStandardAllowanceMm(request.standardAllowanceMm());
        current.setLooseAllowanceMm(request.looseAllowanceMm());
        current.setToleranceMm(request.toleranceMm());
        current.setMaxBeadCount(request.maxBeadCount());
        current.setConfigVersion(current.getConfigVersion() + 1);
        ruleConfigMapper.updateById(current);
        return getPublicConfiguration();
    }

    /** 保存固定视图渲染配置并递增配置版本。 */
    @Transactional
    public AdminDiyConfigurationView saveVisualConfig(
            SaveDiyVisualConfigRequest request,
            String actor
    ) {
        DiyVisualConfig current = requireVisualConfig();
        current.setCanvasBackgroundColor(request.canvasBackgroundColor());
        current.setBraceletShadowOpacity(request.braceletShadowOpacity());
        current.setBeadShadowOpacity(request.beadShadowOpacity());
        current.setBeadOverlapRatio(request.beadOverlapRatio());
        current.setConfigVersion(current.getConfigVersion() + 1);
        current.setUpdatedBy(actor);
        visualConfigMapper.updateById(current);
        return getAdminConfiguration();
    }

    /**
     * 整体替换工艺费规则。
     *
     * @param rules 新区间集合，必须从 1 连续覆盖到当前最大珠数
     * @return 更新后的公开配置
     */
    @Transactional
    public DiyConfigurationView replaceCraftFeeRules(
            List<SaveCraftFeeRulesRequest.CraftFeeRuleInput> rules
    ) {
        int maxBeadCount = requireRuleConfig().getMaxBeadCount();
        List<SaveCraftFeeRulesRequest.CraftFeeRuleInput> sorted =
                validateCraftFeeRules(rules, maxBeadCount);
        long nextVersion = currentCraftFeeVersion() + 1;
        craftFeeRuleMapper.deleteAll();
        int sortOrder = 10;
        for (SaveCraftFeeRulesRequest.CraftFeeRuleInput input : sorted) {
            CraftFeeRule rule = new CraftFeeRule();
            rule.setMinBeadCount(input.minBeadCount());
            rule.setMaxBeadCount(input.maxBeadCount());
            rule.setFee(input.fee());
            rule.setStatus(OptionStatus.ENABLED);
            rule.setSortOrder(sortOrder);
            rule.setRuleVersion(nextVersion);
            craftFeeRuleMapper.insert(rule);
            sortOrder += 10;
        }
        return getPublicConfiguration();
    }

    /**
     * 创建绳材选项。
     *
     * @param request 绳材请求
     * @return 更新后的管理配置
     */
    @Transactional
    public AdminDiyConfigurationView createCordOption(
            SaveCordOptionRequest request
    ) {
        CordOption cordOption = new CordOption();
        applyCordRequest(cordOption, request);
        cordOption.setOptionVersion(1L);
        cordOptionMapper.insert(cordOption);
        return getAdminConfiguration();
    }

    /**
     * 更新绳材选项并递增版本。
     *
     * @param id 绳材主键
     * @param request 绳材请求
     * @return 更新后的管理配置
     */
    @Transactional
    public AdminDiyConfigurationView updateCordOption(
            Long id,
            SaveCordOptionRequest request
    ) {
        CordOption cordOption = cordOptionMapper.selectById(id);
        if (cordOption == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_NOT_FOUND,
                    "绳材不存在"
            );
        }
        applyCordRequest(cordOption, request);
        cordOption.setOptionVersion(cordOption.getOptionVersion() + 1);
        cordOptionMapper.updateById(cordOption);
        return getAdminConfiguration();
    }

    private List<SaveCraftFeeRulesRequest.CraftFeeRuleInput>
            validateCraftFeeRules(
                    List<SaveCraftFeeRulesRequest.CraftFeeRuleInput> rules,
                    int maxBeadCount
            ) {
        if (rules == null || rules.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "工艺费规则不能为空"
            );
        }
        List<SaveCraftFeeRulesRequest.CraftFeeRuleInput> sorted = rules.stream()
                .sorted(Comparator.comparing(
                        SaveCraftFeeRulesRequest.CraftFeeRuleInput::minBeadCount
                ))
                .toList();
        int expectedMin = 1;
        for (SaveCraftFeeRulesRequest.CraftFeeRuleInput rule : sorted) {
            if (rule.maxBeadCount() < rule.minBeadCount()) {
                throw new BusinessException(
                        ErrorCode.COMMON_BAD_REQUEST,
                        "工艺费区间结束值不能小于开始值"
                );
            }
            if (rule.minBeadCount() < expectedMin) {
                throw new BusinessException(
                        ErrorCode.COMMON_BAD_REQUEST,
                        "工艺费区间不能重叠"
                );
            }
            if (rule.minBeadCount() > expectedMin) {
                throw new BusinessException(
                        ErrorCode.COMMON_BAD_REQUEST,
                        "工艺费区间不能留空档"
                );
            }
            expectedMin = rule.maxBeadCount() + 1;
        }
        if (expectedMin != maxBeadCount + 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "工艺费区间必须覆盖 1 到最大珠数"
            );
        }
        return sorted;
    }

    private DiyRuleConfig requireRuleConfig() {
        DiyRuleConfig ruleConfig = ruleConfigMapper.selectById(SINGLETON_RULE_ID);
        if (ruleConfig == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_INTERNAL_ERROR,
                    "DIY 尺寸规则缺失"
            );
        }
        return ruleConfig;
    }

    private DiyVisualConfig requireVisualConfig() {
        DiyVisualConfig visualConfig = visualConfigMapper.selectById(
                SINGLETON_VISUAL_CONFIG_ID
        );
        if (visualConfig == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_INTERNAL_ERROR,
                    "DIY 视图渲染配置缺失"
            );
        }
        return visualConfig;
    }

    private List<CraftFeeRule> enabledCraftFeeRules() {
        return craftFeeRuleMapper.selectList(
                Wrappers.lambdaQuery(CraftFeeRule.class)
                        .eq(CraftFeeRule::getStatus, OptionStatus.ENABLED)
                        .orderByAsc(CraftFeeRule::getMinBeadCount)
                        .orderByAsc(CraftFeeRule::getId)
        );
    }

    private List<CordOption> enabledCordOptions() {
        return cordOptionMapper.selectList(
                Wrappers.lambdaQuery(CordOption.class)
                        .eq(CordOption::getStatus, OptionStatus.ENABLED)
                        .orderByAsc(CordOption::getSortOrder)
                        .orderByAsc(CordOption::getId)
        );
    }

    private List<CordOption> allCordOptions() {
        return cordOptionMapper.selectList(
                Wrappers.lambdaQuery(CordOption.class)
                        .orderByAsc(CordOption::getSortOrder)
                        .orderByAsc(CordOption::getId)
        );
    }

    private long currentCraftFeeVersion() {
        return craftFeeRuleMapper.selectList(
                        Wrappers.lambdaQuery(CraftFeeRule.class)
                )
                .stream()
                .map(CraftFeeRule::getRuleVersion)
                .filter(version -> version != null)
                .max(Long::compareTo)
                .orElse(0L);
    }

    private void applyCordRequest(
            CordOption cordOption,
            SaveCordOptionRequest request
    ) {
        cordOption.setCode(request.code());
        cordOption.setName(request.name());
        cordOption.setColorHex(request.colorHex());
        cordOption.setExtraFee(request.extraFee());
        cordOption.setStatus(request.status());
        cordOption.setSortOrder(request.sortOrder());
    }

    private DiyConfigurationView toPublicView(
            DiyRuleConfig rule,
            List<CraftFeeRule> craftFeeRules,
            List<CordOption> cordOptions,
            DiyVisualConfig visualConfig
    ) {
        return new DiyConfigurationView(
                toRuleView(rule),
                craftFeeRules.stream().map(this::toCraftFeeView).toList(),
                cordOptions.stream().map(this::toPublicCordView).toList(),
                toVisualConfigView(visualConfig)
        );
    }

    private AdminDiyConfigurationView toAdminView(
            DiyRuleConfig rule,
            List<CraftFeeRule> craftFeeRules,
            List<CordOption> cordOptions,
            DiyVisualConfig visualConfig
    ) {
        return new AdminDiyConfigurationView(
                toRuleView(rule),
                craftFeeRules.stream().map(this::toCraftFeeView).toList(),
                cordOptions.stream().map(this::toAdminCordView).toList(),
                toVisualConfigView(visualConfig)
        );
    }

    private DiyConfigurationView.Rule toRuleView(DiyRuleConfig rule) {
        return new DiyConfigurationView.Rule(
                rule.getMinWristMm(),
                rule.getMaxWristMm(),
                rule.getSnugAllowanceMm(),
                rule.getStandardAllowanceMm(),
                rule.getLooseAllowanceMm(),
                rule.getToleranceMm(),
                rule.getMaxBeadCount(),
                rule.getConfigVersion()
        );
    }

    private DiyConfigurationView.VisualConfig toVisualConfigView(
            DiyVisualConfig visualConfig
    ) {
        return new DiyConfigurationView.VisualConfig(
                visualConfig.getCanvasBackgroundColor(),
                visualConfig.getBraceletShadowOpacity(),
                visualConfig.getBeadShadowOpacity(),
                visualConfig.getBeadOverlapRatio(),
                visualConfig.getConfigVersion()
        );
    }

    private DiyConfigurationView.CraftFee toCraftFeeView(CraftFeeRule rule) {
        return new DiyConfigurationView.CraftFee(
                rule.getId(),
                rule.getMinBeadCount(),
                rule.getMaxBeadCount(),
                rule.getFee(),
                rule.getRuleVersion()
        );
    }

    private DiyConfigurationView.Cord toPublicCordView(CordOption cordOption) {
        return new DiyConfigurationView.Cord(
                cordOption.getId(),
                cordOption.getCode(),
                cordOption.getName(),
                cordOption.getColorHex(),
                cordOption.getExtraFee(),
                cordOption.getOptionVersion()
        );
    }

    private AdminDiyConfigurationView.Cord toAdminCordView(
            CordOption cordOption
    ) {
        return new AdminDiyConfigurationView.Cord(
                cordOption.getId(),
                cordOption.getCode(),
                cordOption.getName(),
                cordOption.getColorHex(),
                cordOption.getExtraFee(),
                cordOption.getStatus(),
                cordOption.getSortOrder(),
                cordOption.getOptionVersion()
        );
    }
}
