package com.diybracelet.mall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DIY 手串定制商城后端启动入口。
 *
 * <p>业务代码按照领域模块组织，避免形成跨模块共享的巨大 Service 层。</p>
 */
@SpringBootApplication
public class DiyBraceletMallApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args JVM 传入的命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DiyBraceletMallApplication.class, args);
    }
}
