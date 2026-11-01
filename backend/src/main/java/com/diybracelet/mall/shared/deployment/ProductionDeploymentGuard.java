package com.diybracelet.mall.shared.deployment;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;


/** 在数据源和迁移启动前阻止演示身份、模拟支付配置被用于正式部署。 */
@Configuration(proxyBeanMethods = false)
@Profile({"prod", "production"})
public class ProductionDeploymentGuard {
    @Bean
    static BeanFactoryPostProcessor rejectUnsupportedProductionDeployment() {
        return beanFactory -> {
            throw new IllegalStateException(
                    "当前版本尚未接入真实微信身份和支付，禁止使用 prod/production 配置部署。"
                    + "本地演示请使用默认配置；正式部署需先完成真实身份、支付与密钥管理。"
            );
        };
    }
}
