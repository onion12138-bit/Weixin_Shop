package com.diybracelet.mall.commerce.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.commerce.domain.CommerceRuleConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 交易规则配置 Mapper。 */
@Mapper
public interface CommerceRuleConfigMapper extends BaseMapper<CommerceRuleConfig> {

    @Select("select * from commerce_rule_config where id = #{id} for update")
    CommerceRuleConfig selectByIdForUpdate(Long id);
}
