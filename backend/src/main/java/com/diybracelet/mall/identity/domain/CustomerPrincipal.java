package com.diybracelet.mall.identity.domain;

/**
 * 小程序顾客侧接口的安全主体。
 *
 * <p>当前阶段主体来自匿名访客身份；后续微信 OpenID、手机号或会员账号接入后，
 * 仍映射到同一个顾客身份主键，DIY、订单等业务模块不需要关心外部身份来源。</p>
 *
 * @param customerIdentityId 顾客身份主键
 */
public record CustomerPrincipal(Long customerIdentityId) {

    public CustomerPrincipal {
        if (customerIdentityId == null || customerIdentityId <= 0) {
            throw new IllegalArgumentException("顾客身份主键必须大于零");
        }
    }
}
