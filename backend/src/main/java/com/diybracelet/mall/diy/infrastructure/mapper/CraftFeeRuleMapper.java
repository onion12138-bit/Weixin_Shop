package com.diybracelet.mall.diy.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.diy.domain.CraftFeeRule;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工艺费规则 Mapper。
 */
@Mapper
public interface CraftFeeRuleMapper extends BaseMapper<CraftFeeRule> {

    /**
     * 整体替换工艺费区间前清空旧规则。
     *
     * @return 删除行数
     */
    @Delete("delete from craft_fee_rule")
    int deleteAll();
}
