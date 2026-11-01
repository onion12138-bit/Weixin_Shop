package com.diybracelet.mall.customer.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.diybracelet.mall.customer.domain.CouponTemplate;
import org.apache.ibatis.annotations.Mapper;


/** 优惠券模板数据访问入口。 */
@Mapper
public interface CouponTemplateMapper extends BaseMapper<CouponTemplate> { }
