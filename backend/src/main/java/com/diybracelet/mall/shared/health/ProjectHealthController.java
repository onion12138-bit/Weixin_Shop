package com.diybracelet.mall.shared.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供不依赖数据库的项目身份检查。
 *
 * <p>该接口用于确认前端代理和后端进程已正确连接；基础设施状态由 Actuator
 * 的健康接口负责，二者职责不同。</p>
 */
@RestController
@RequestMapping("/api/public")
public class ProjectHealthController {

    /**
     * 返回项目名称和进程状态。
     *
     * @return 可供前端启动页展示的稳定字段
     */
    @GetMapping("/project-health")
    public Map<String, String> projectHealth() {
        return Map.of("name", "DIY 手串定制商城", "status", "UP");
    }
}
