package com.diybracelet.mall.identity.domain;

/**
 * 从当前请求上下文解析顾客身份的端口。
 *
 * <p>业务服务只依赖这个接口获取“当前顾客是谁”，不直接读取 Spring Security。
 * 这样未来替换为微信登录、会员账号或账号合并时，可以只调整身份适配层。</p>
 */
public interface CustomerIdentityResolver {

    /**
     * 返回当前请求中的顾客身份主键。
     *
     * @return 顾客身份主键
     */
    Long requireCustomerIdentityId();
}
