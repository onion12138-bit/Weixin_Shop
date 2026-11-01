package com.diybracelet.mall.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 需要真实 MySQL 语义的后端集成测试基类。
 *
 * <p>测试容器每次从空数据库启动，Spring Boot 通过 {@link ServiceConnection}
 * 自动覆盖本地数据源配置，然后由 Flyway 建表并写入演示数据。这样可以发现 H2
 * 无法暴露的 MySQL JSON、外键、排序和精度问题。</p>
 */
@SpringBootTest
@TestPropertySource(properties = "app.orders.expiry-enabled=false")
public abstract class MySqlIntegrationTest {

    /**
     * 同一测试 JVM 内共享且只启动一次的容器。
     *
     * <p>不能在这里使用 JUnit 的 {@code @Container} 类级生命周期：继承本基类的
     * 每个测试类都会停止并重新启动同一个静态容器，而 Spring 可能复用上一测试类
     * 的应用上下文，最终让连接池继续指向已经失效的旧端口。手动启动后由 Ryuk 在
     * JVM 退出时清理，既避免端口失效，也显著减少整套集成测试的启动时间。</p>
     */
    @ServiceConnection
    protected static final MySQLContainer<?> MYSQL =
        new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("diy_bracelet_test")
            .withUsername("test")
            .withPassword("test");

    static {
        MYSQL.start();
    }
}
