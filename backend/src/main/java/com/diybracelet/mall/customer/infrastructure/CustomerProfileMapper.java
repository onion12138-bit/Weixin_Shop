package com.diybracelet.mall.customer.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.customer.domain.CustomerProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerProfileMapper extends BaseMapper<CustomerProfile> { }
