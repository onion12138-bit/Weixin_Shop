package com.diybracelet.mall.shared.deployment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionDeploymentGuardTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> new ClassPathBeanDefinitionScanner(
                    (org.springframework.beans.factory.support.BeanDefinitionRegistry) context.getBeanFactory(),
                    true, context.getEnvironment())
                    .scan("com.diybracelet.mall.shared.deployment"));

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production"})
    void productionMustFailBeforeInstantiatingApplicationServices(String profile) {
        var initialized = new java.util.concurrent.atomic.AtomicBoolean();
        runner.withPropertyValues("spring.profiles.active=" + profile)
                .withBean("applicationService", Object.class, () -> {
                    initialized.set(true);
                    return new Object();
                })
                .run(context -> {
                    assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("尚未接入真实微信身份和支付");
                    assertThat(initialized).isFalse();
                });
    }

    @Test
    void defaultLocalDemoProfileRemainsAvailable() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }
}
