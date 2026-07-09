package com.diybracelet.mall.identity.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.identity.domain.CustomerIdentity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 顾客身份表的数据访问入口。
 */
@Mapper
public interface CustomerIdentityMapper
        extends BaseMapper<CustomerIdentity> {
}
