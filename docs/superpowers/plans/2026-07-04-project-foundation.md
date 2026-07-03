# Project Foundation and Three-Client Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a documented, locally runnable repository containing the Spring Boot service, Vue 3 administration client, native WeChat mini program, and local MySQL/Redis infrastructure.

**Architecture:** Start with one repository and three independently runnable applications. The backend is a modular monolith and owns all business data; the two clients communicate through REST APIs. This plan builds only the engineering foundation and health surfaces, leaving catalog behavior to the next vertical-slice plan.

**Tech Stack:** JDK 17, Spring Boot 3.2.12, Maven, Vue 3, TypeScript, Vite, Vitest, native WeChat mini program, Jest, MySQL 8, Redis 7, Docker Compose

**Execution status:** Completed on 2026-07-04 in branch `codex/project-foundation`; prerequisite checks, repository verification, Vue production build, and MySQL/Redis health checks passed.

---

## Scope and prerequisites

This is the first implementation plan from the approved design specification:

- Design: `docs/superpowers/specs/2026-07-04-diy-bracelet-mall-design.md`
- Project root: `/Users/onion/Desktop/AI_Testing/Weixin_Shop`
- Current machine state: JDK 8 is installed; Maven and Docker are not installed; Node.js 23 and pnpm 11 are installed.

Before Task 2, the user manually installs JDK 17 and Maven. Before Task 7, the user manually installs Docker Desktop. Codex only verifies the installed versions and does not run software installers.

## Target file structure

```text
Weixin_Shop/
├── .editorconfig
├── .env.example
├── .gitignore
├── CHANGELOG.md
├── README.md
├── docker-compose.yml
├── backend/
│   ├── pom.xml
│   └── src/
├── admin-web/
│   ├── package.json
│   └── src/
├── miniprogram/
│   ├── package.json
│   ├── project.config.json
│   └── miniprogram/
├── docs/
│   ├── requirements.md
│   ├── architecture.md
│   ├── data-model.md
│   ├── api.md
│   ├── development.md
│   ├── setup.md
│   ├── deployment.md
│   └── testing.md
├── scripts/
│   ├── check-prerequisites.sh
│   └── verify.sh
└── storage/
    └── .gitkeep
```

### Task 1: Establish repository policy and authoritative documentation

**Files:**

- Create: `.gitignore`
- Create: `.editorconfig`
- Create: `.env.example`
- Create: `README.md`
- Create: `CHANGELOG.md`
- Create: `docs/requirements.md`
- Create: `docs/architecture.md`
- Create: `docs/data-model.md`
- Create: `docs/api.md`
- Create: `docs/development.md`
- Create: `docs/setup.md`
- Create: `docs/deployment.md`
- Create: `docs/testing.md`
- Create: `storage/.gitkeep`

- [ ] **Step 1: Add repository ignore rules**

Create `.gitignore` with:

```gitignore
.DS_Store
.idea/
.vscode/
*.iml

.env
.env.*
!.env.example

backend/target/
admin-web/node_modules/
admin-web/dist/
miniprogram/node_modules/
miniprogram/coverage/

storage/*
!storage/.gitkeep

*.log
coverage/
playwright-report/
test-results/
```

- [ ] **Step 2: Add cross-language editor rules**

Create `.editorconfig` with:

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
indent_style = space
indent_size = 2
trim_trailing_whitespace = true

[*.java]
indent_size = 4

[*.md]
trim_trailing_whitespace = false
```

- [ ] **Step 3: Add non-secret environment defaults**

Create `.env.example` with:

```dotenv
MYSQL_DATABASE=diy_bracelet
MYSQL_USER=diy_bracelet
MYSQL_PASSWORD=change-me
MYSQL_ROOT_PASSWORD=change-root-password
MYSQL_PORT=3306
REDIS_PORT=6379

BACKEND_PORT=8080
JWT_SECRET=replace-with-at-least-32-characters
LOCAL_STORAGE_PATH=../storage
VITE_API_BASE_URL=http://localhost:8080/api
```

- [ ] **Step 4: Add the project overview**

Create `README.md` containing these exact top-level sections:

```markdown
# DIY 手串定制商城

一个由 Spring Boot 后端、Vue 3 管理后台和原生微信小程序组成的 DIY 手串定制商品系统。

## 当前状态

工程基础阶段：建立三端可启动骨架、运行环境、测试入口和权威文档。

## 核心能力

- 珠子 SKU、套餐、库存和素材管理
- Canvas 2D DIY 排珠与实时计价
- 规则推荐、方案保存和预览图
- 订单、库存预占、模拟支付和生产 BOM
- 管理后台履约、用户和运营配置

## 项目结构

- `backend/`：Spring Boot 模块化单体
- `admin-web/`：Vue 3 管理后台
- `miniprogram/`：原生微信小程序
- `docs/`：当前有效的项目文档
- `scripts/`：环境检查与统一验证脚本
- `storage/`：本地上传文件，运行数据不提交

## 快速开始

完整步骤见 `docs/setup.md`。首先执行：

```bash
./scripts/check-prerequisites.sh
```

## 权威文档

- `docs/requirements.md`
- `docs/architecture.md`
- `docs/data-model.md`
- `docs/api.md`
- `docs/development.md`
- `docs/setup.md`
- `docs/deployment.md`
- `docs/testing.md`

## 设计与计划

- 最新设计：`docs/superpowers/specs/2026-07-04-diy-bracelet-mall-design.md`
- 当前计划：`docs/superpowers/plans/2026-07-04-project-foundation.md`
```

- [ ] **Step 5: Create current-version documentation**

Create the eight files under `docs/`. Each file must state that it is the current effective document, link to the approved design specification, and contain the following responsibility:

```markdown
<!-- docs/requirements.md -->
# 当前需求

本文档是项目当前有效需求总览。完整、已确认的范围见
`docs/superpowers/specs/2026-07-04-diy-bracelet-mall-design.md`。

## V1 目标

交付真实可用的 Spring Boot 后端、Vue 3 管理后台和原生微信小程序。
真实支付、微信授权、云存储和大模型通过适配器延后接入。

## 当前实施阶段

工程基础与三端可启动骨架。
```

```markdown
<!-- docs/architecture.md -->
# 系统架构

当前架构为 Spring Boot 模块化单体。小程序与管理后台共享 REST API，
但使用独立身份和权限。MySQL 保存业务数据，Redis 提供缓存和分布式协调，
文件先保存到本地目录。

完整模块边界与外部适配设计见已确认设计规格。
```

```markdown
<!-- docs/data-model.md -->
# 数据模型

本文件记录当前已经实现的数据表、字段、索引和关系。

工程基础阶段尚未创建业务表。数据库迁移必须由 Flyway 管理，
后续每个业务计划在本文件追加已落地的数据字典。
```

```markdown
<!-- docs/api.md -->
# API 约定

- 小程序接口：`/api/app/**`
- 管理后台接口：`/api/admin/**`
- 公共接口：`/api/public/**`
- 健康检查：`/actuator/health`

业务接口统一返回 `code`、`message`、`data`、`traceId` 和 `timestamp`。
```

```markdown
<!-- docs/development.md -->
# 开发规范

## 注释

- Java 类、接口和公共方法使用中文 Javadoc。
- 计价、库存、订单、状态机、支付和文件安全说明原因、边界和失败策略。
- TypeScript 公共函数、组件职责和 Canvas 算法提供说明。
- 不添加只重复代码字面含义的注释。

## 提交

每个提交只完成一个可验证目标，提交前运行对应模块测试。
```

```markdown
<!-- docs/setup.md -->
# 本地启动

## 前置依赖

- JDK 17
- Maven 3.9+
- Node.js 20+
- pnpm 9+
- Docker 与 Docker Compose

## 顺序

1. 复制 `.env.example` 为 `.env` 并修改本地密码。
2. 执行 `docker compose up -d`。
3. 在 `backend/` 执行 `mvn spring-boot:run`。
4. 在 `admin-web/` 执行 `pnpm install && pnpm dev`。
5. 使用微信开发者工具导入 `miniprogram/`。
```

```markdown
<!-- docs/deployment.md -->
# 部署说明

生产环境必须使用独立密钥和数据库账号，不得使用 `.env.example` 中的值。
当前阶段只支持本地开发部署；生产构建、反向代理和备份策略将在产品加固阶段补齐。
```

```markdown
<!-- docs/testing.md -->
# 测试说明

- 后端：`cd backend && mvn test`
- 管理后台：`cd admin-web && pnpm test`
- 小程序逻辑：`cd miniprogram && pnpm test`
- 全仓验证：`./scripts/verify.sh`

每个功能遵循先写失败测试、再写最小实现、最后重构的顺序。
```

- [ ] **Step 6: Add changelog and storage marker**

Create `CHANGELOG.md`:

```markdown
# 变更记录

## Unreleased

- 建立项目设计规格和工程基础实施计划。
```

Create an empty `storage/.gitkeep`.

- [ ] **Step 7: Verify documentation structure**

Run:

```bash
test -f README.md
test -f docs/requirements.md
test -f docs/development.md
test -f storage/.gitkeep
rg -n "当前有效|当前架构|当前需求" README.md docs
```

Expected: all `test` commands return exit code 0 and `rg` lists the current-version statements.

- [ ] **Step 8: Commit**

```bash
git add .gitignore .editorconfig .env.example README.md CHANGELOG.md docs storage/.gitkeep
git commit -m "docs: establish project documentation baseline"
```

### Task 2: Add environment prerequisite checks

**Files:**

- Create: `scripts/check-prerequisites.sh`
- Test: shell execution of `scripts/check-prerequisites.sh`

- [ ] **Step 1: Write the prerequisite checker**

Create `scripts/check-prerequisites.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

failures=0

require_command() {
  local command_name="$1"
  local install_hint="$2"

  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "缺少 ${command_name}：${install_hint}" >&2
    failures=$((failures + 1))
  fi
}

require_command java "安装 JDK 17"
require_command mvn "安装 Maven 3.9+"
require_command node "安装 Node.js 20+"
require_command pnpm "安装 pnpm 9+"
require_command docker "安装 Docker Desktop 或兼容运行时"

if command -v java >/dev/null 2>&1; then
  java_major="$(java -version 2>&1 | awk -F '[".]' '/version/ {print ($2 == 1 ? $3 : $2); exit}')"
  if [[ -z "${java_major}" || "${java_major}" -lt 17 ]]; then
    echo "JDK 版本必须为 17 或更高，当前为 ${java_major:-未知}" >&2
    failures=$((failures + 1))
  fi
fi

if [[ "${failures}" -ne 0 ]]; then
  echo "环境检查失败：${failures} 项不满足要求。" >&2
  exit 1
fi

echo "环境检查通过。"
```

- [ ] **Step 2: Make the script executable and verify current failure**

Run:

```bash
chmod +x scripts/check-prerequisites.sh
./scripts/check-prerequisites.sh
```

Expected before installing prerequisites: exit code 1, reporting JDK 17, Maven, and Docker requirements.

- [ ] **Step 3: Verify manually installed prerequisites**

Install these Apple Silicon packages from their official pages:

- Eclipse Temurin JDK `17.0.19+10`, macOS AArch64 JDK package:
  `https://adoptium.net/temurin/releases/?arch=aarch64&os=mac&version=17`
- Apache Maven `3.9.16` binary distribution:
  `https://maven.apache.org/download.cgi`
- Docker Desktop `4.80.0` or a newer stable release, Mac with Apple chip:
  `https://docs.docker.com/desktop/setup/install/mac-install/`

Start Docker Desktop and wait until it reports that the engine is running. Open a new terminal, then run:

Run again:

```bash
java -version
mvn -version
docker version
docker compose version
./scripts/check-prerequisites.sh
```

Expected: Java reports `17.0.19` or another Java 17 security update, Maven reports `3.9.16` or a newer 3.9.x release, Docker Desktop reports `4.80.0` or newer, and the final command prints `环境检查通过。`

- [ ] **Step 4: Commit**

```bash
git add scripts/check-prerequisites.sh
git commit -m "build: add prerequisite verification"
```

### Task 3: Scaffold the Spring Boot backend with a tested health endpoint

**Files:**

- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/diybracelet/mall/DiyBraceletMallApplication.java`
- Create: `backend/src/main/java/com/diybracelet/mall/shared/health/ProjectHealthController.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/java/com/diybracelet/mall/shared/health/ProjectHealthControllerTest.java`

- [ ] **Step 1: Create the Maven build**

Create `backend/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.12</version>
        <relativePath/>
    </parent>

    <groupId>com.diybracelet</groupId>
    <artifactId>diy-bracelet-mall-backend</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>diy-bracelet-mall-backend</name>
    <description>DIY 手串定制商城后端服务</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Write the failing controller test**

Create `ProjectHealthControllerTest.java`:

```java
package com.diybracelet.mall.shared.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectHealthController.class)
class ProjectHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeProjectIdentity() throws Exception {
        mockMvc.perform(get("/api/public/project-health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("DIY 手串定制商城"))
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
```

- [ ] **Step 3: Run the test and verify it fails**

Run:

```bash
cd backend
mvn -Dtest=ProjectHealthControllerTest test
```

Expected: compilation fails because `ProjectHealthController` does not exist.

- [ ] **Step 4: Add the application entry point**

Create `DiyBraceletMallApplication.java`:

```java
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

    public static void main(String[] args) {
        SpringApplication.run(DiyBraceletMallApplication.class, args);
    }
}
```

- [ ] **Step 5: Implement the minimal health controller**

Create `ProjectHealthController.java`:

```java
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
```

- [ ] **Step 6: Add local configuration**

Create `application.yml`:

```yaml
spring:
  application:
    name: diy-bracelet-mall-backend

server:
  port: ${BACKEND_PORT:8080}

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: never
```

- [ ] **Step 7: Run backend tests**

Run:

```bash
cd backend
mvn test
```

Expected: `BUILD SUCCESS` and one passing test.

- [ ] **Step 8: Commit**

```bash
git add backend
git commit -m "feat: scaffold Spring Boot backend"
```

### Task 4: Add the shared API response and exception contract

**Files:**

- Create: `backend/src/main/java/com/diybracelet/mall/shared/api/ApiResponse.java`
- Create: `backend/src/main/java/com/diybracelet/mall/shared/error/ErrorCode.java`
- Create: `backend/src/main/java/com/diybracelet/mall/shared/error/BusinessException.java`
- Create: `backend/src/main/java/com/diybracelet/mall/shared/error/GlobalExceptionHandler.java`
- Create: `backend/src/test/java/com/diybracelet/mall/shared/error/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Write the failing exception contract test**

Create `GlobalExceptionHandlerTest.java`:

```java
package com.diybracelet.mall.shared.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(GlobalExceptionHandlerTest.FailingController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldConvertBusinessExceptionToStableResponse() throws Exception {
        mockMvc.perform(get("/test/business-error"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("COMMON_CONFLICT"))
            .andExpect(jsonPath("$.message").value("状态冲突"))
            .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @RestController
    static class FailingController {

        @GetMapping("/test/business-error")
        void fail() {
            throw new BusinessException(ErrorCode.COMMON_CONFLICT, "状态冲突");
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
cd backend
mvn -Dtest=GlobalExceptionHandlerTest test
```

Expected: compilation fails because the response and error classes do not exist.

- [ ] **Step 3: Add the response and error types**

Create `ApiResponse.java`:

```java
package com.diybracelet.mall.shared.api;

import java.time.Instant;

/**
 * 三端接口的统一响应结构。
 *
 * @param code 稳定业务码，客户端不得依赖异常类名
 * @param message 面向当前调用方的简短说明
 * @param data 成功数据，失败时通常为 {@code null}
 * @param traceId 服务端链路标识
 * @param timestamp 服务端生成响应的时间
 * @param <T> 数据类型
 */
public record ApiResponse<T>(
    String code,
    String message,
    T data,
    String traceId,
    Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("OK", "成功", data, traceId, Instant.now());
    }

    public static ApiResponse<Void> failure(
        String code,
        String message,
        String traceId
    ) {
        return new ApiResponse<>(code, message, null, traceId, Instant.now());
    }
}
```

Create `ErrorCode.java`:

```java
package com.diybracelet.mall.shared.error;

import org.springframework.http.HttpStatus;

/**
 * 跨模块通用错误码；领域模块后续定义自己的错误码枚举。
 */
public enum ErrorCode {
    COMMON_BAD_REQUEST(HttpStatus.BAD_REQUEST),
    COMMON_UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    COMMON_FORBIDDEN(HttpStatus.FORBIDDEN),
    COMMON_NOT_FOUND(HttpStatus.NOT_FOUND),
    COMMON_CONFLICT(HttpStatus.CONFLICT),
    COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
```

Create `BusinessException.java`:

```java
package com.diybracelet.mall.shared.error;

/**
 * 表示调用方可以理解和修正的业务失败。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
```

- [ ] **Step 4: Add the global exception handler**

Create `GlobalExceptionHandler.java`:

```java
package com.diybracelet.mall.shared.error;

import com.diybracelet.mall.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将服务端异常转换为稳定 API 契约。
 *
 * <p>当前使用随机 traceId。接入日志链路后，应优先复用请求上下文中的 traceId，
 * 以便用户看到的标识能够对应服务端日志。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
        BusinessException exception
    ) {
        String traceId = UUID.randomUUID().toString();
        return ResponseEntity
            .status(exception.errorCode().status())
            .body(ApiResponse.failure(
                exception.errorCode().name(),
                exception.getMessage(),
                traceId
            ));
    }
}
```

- [ ] **Step 5: Run all backend tests**

Run:

```bash
cd backend
mvn test
```

Expected: `BUILD SUCCESS` and both controller test classes pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src
git commit -m "feat: add shared API error contract"
```

### Task 5: Scaffold and test the Vue administration client

**Files:**

- Create: `admin-web/package.json`
- Create: `admin-web/tsconfig.json`
- Create: `admin-web/vite.config.ts`
- Create: `admin-web/index.html`
- Create: `admin-web/src/main.ts`
- Create: `admin-web/src/App.vue`
- Create: `admin-web/src/style.css`
- Create: `admin-web/src/components/ProjectStatus.vue`
- Create: `admin-web/src/components/ProjectStatus.spec.ts`

- [ ] **Step 1: Create the package manifest**

Create `admin-web/package.json`:

```json
{
  "name": "diy-bracelet-mall-admin",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "test": "vitest run"
  },
  "dependencies": {
    "vue": "3.5.13"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "5.2.1",
    "@vue/test-utils": "2.4.6",
    "jsdom": "25.0.1",
    "typescript": "5.7.3",
    "vite": "5.4.14",
    "vitest": "2.1.8",
    "vue-tsc": "2.2.0"
  },
  "packageManager": "pnpm@11.7.0"
}
```

- [ ] **Step 2: Add TypeScript and test configuration**

Create `tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "strict": true,
    "jsx": "preserve",
    "types": ["vitest/globals"],
    "lib": ["ES2022", "DOM"]
  },
  "include": ["src/**/*.ts", "src/**/*.vue", "vite.config.ts"]
}
```

Create `vite.config.ts`:

```ts
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
  },
})
```

- [ ] **Step 3: Write the failing component test**

Create `ProjectStatus.spec.ts`:

```ts
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProjectStatus from './ProjectStatus.vue'

describe('ProjectStatus', () => {
  it('shows the three project applications', () => {
    const wrapper = mount(ProjectStatus)

    expect(wrapper.text()).toContain('Spring Boot 后端')
    expect(wrapper.text()).toContain('Vue 管理后台')
    expect(wrapper.text()).toContain('微信小程序')
  })
})
```

- [ ] **Step 4: Install packages and verify the test fails**

Run:

```bash
cd admin-web
pnpm install
pnpm test
```

Expected: test compilation fails because `ProjectStatus.vue` does not exist.

- [ ] **Step 5: Implement the administration shell**

Create `ProjectStatus.vue`:

```vue
<script setup lang="ts">
const applications = [
  { name: 'Spring Boot 后端', state: '基础工程' },
  { name: 'Vue 管理后台', state: '基础工程' },
  { name: '微信小程序', state: '基础工程' },
] as const
</script>

<template>
  <section aria-labelledby="project-status-title" class="status-panel">
    <h2 id="project-status-title">项目组成</h2>
    <ul>
      <li v-for="application in applications" :key="application.name">
        <strong>{{ application.name }}</strong>
        <span>{{ application.state }}</span>
      </li>
    </ul>
  </section>
</template>
```

Create `App.vue`:

```vue
<script setup lang="ts">
import ProjectStatus from './components/ProjectStatus.vue'
</script>

<template>
  <main class="page-shell">
    <p class="eyebrow">DIY BRACELET MALL</p>
    <h1>DIY 手串定制商城管理后台</h1>
    <p class="lead">管理商品、库存、DIY 规则、订单与生产履约。</p>
    <ProjectStatus />
  </main>
</template>
```

Create `main.ts`:

```ts
import { createApp } from 'vue'
import App from './App.vue'
import './style.css'

createApp(App).mount('#app')
```

Create `style.css`:

```css
:root {
  color: #2f2430;
  background: #f8f4f0;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif;
}

body {
  margin: 0;
}

.page-shell {
  box-sizing: border-box;
  min-height: 100vh;
  padding: 64px clamp(24px, 7vw, 96px);
}

.eyebrow {
  color: #8a5c69;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.lead {
  color: #6e6268;
  font-size: 18px;
}

.status-panel {
  margin-top: 40px;
  max-width: 720px;
  padding: 24px;
  border: 1px solid #e4d7d2;
  border-radius: 16px;
  background: #fff;
}

.status-panel ul {
  display: grid;
  gap: 12px;
  padding: 0;
  list-style: none;
}

.status-panel li {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #f0e8e4;
}
```

Create `index.html`:

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>DIY 手串定制商城管理后台</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

- [ ] **Step 6: Run tests and production build**

Run:

```bash
cd admin-web
pnpm test
pnpm build
```

Expected: one passing Vitest test and a successful Vite build.

- [ ] **Step 7: Commit**

```bash
git add admin-web
git commit -m "feat: scaffold Vue administration client"
```

### Task 6: Scaffold and test the native WeChat mini program

**Files:**

- Create: `miniprogram/package.json`
- Create: `miniprogram/tsconfig.json`
- Create: `miniprogram/project.config.json`
- Create: `miniprogram/miniprogram/app.ts`
- Create: `miniprogram/miniprogram/app.json`
- Create: `miniprogram/miniprogram/app.wxss`
- Create: `miniprogram/miniprogram/sitemap.json`
- Create: `miniprogram/miniprogram/pages/home/index.ts`
- Create: `miniprogram/miniprogram/pages/home/index.json`
- Create: `miniprogram/miniprogram/pages/home/index.wxml`
- Create: `miniprogram/miniprogram/pages/home/index.wxss`
- Create: `miniprogram/src/project-summary.ts`
- Create: `miniprogram/src/project-summary.spec.ts`

- [ ] **Step 1: Add the mini program test package**

Create `miniprogram/package.json`:

```json
{
  "name": "diy-bracelet-mall-miniprogram",
  "private": true,
  "version": "0.1.0",
  "scripts": {
    "test": "jest --runInBand"
  },
  "devDependencies": {
    "@types/jest": "29.5.14",
    "jest": "29.7.0",
    "ts-jest": "29.2.5",
    "typescript": "5.7.3"
  },
  "jest": {
    "preset": "ts-jest",
    "testEnvironment": "node",
    "roots": ["<rootDir>/src"]
  },
  "packageManager": "pnpm@11.7.0"
}
```

Create `tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "CommonJS",
    "strict": true,
    "esModuleInterop": true,
    "types": ["jest"]
  },
  "include": ["src/**/*.ts"]
}
```

- [ ] **Step 2: Write the failing summary test**

Create `project-summary.spec.ts`:

```ts
import { projectSummary } from './project-summary'

describe('projectSummary', () => {
  it('describes the customer journey', () => {
    expect(projectSummary()).toBe('选珠 · DIY · 保存 · 下单 · 生产')
  })
})
```

- [ ] **Step 3: Install packages and verify the test fails**

Run:

```bash
cd miniprogram
pnpm install
pnpm test
```

Expected: test compilation fails because `project-summary.ts` does not exist.

- [ ] **Step 4: Implement the tested summary**

Create `project-summary.ts`:

```ts
/**
 * 返回小程序首屏使用的业务流程摘要。
 *
 * 该函数保持无微信运行时依赖，使核心展示逻辑可以在普通 Jest 环境中验证。
 */
export function projectSummary(): string {
  return '选珠 · DIY · 保存 · 下单 · 生产'
}
```

- [ ] **Step 5: Add the native mini program shell**

Create `project.config.json`:

```json
{
  "appid": "touristappid",
  "projectname": "diy-bracelet-mall",
  "miniprogramRoot": "miniprogram/",
  "compileType": "miniprogram",
  "setting": {
    "es6": true,
    "minified": false,
    "urlCheck": false,
    "useCompilerPlugins": ["typescript"]
  }
}
```

Create `app.ts`:

```ts
/**
 * 小程序全局入口。
 *
 * V1 使用本地演示身份；后续微信登录只替换身份适配器，不改变页面业务状态。
 */
App({
  globalData: {
    apiBaseUrl: 'http://localhost:8080/api',
  },
})
```

Create `app.json`:

```json
{
  "pages": ["pages/home/index"],
  "window": {
    "navigationBarTitleText": "DIY 手串",
    "navigationBarBackgroundColor": "#f8f4f0",
    "navigationBarTextStyle": "black",
    "backgroundColor": "#f8f4f0"
  },
  "sitemapLocation": "sitemap.json"
}
```

Create `app.wxss`:

```css
page {
  color: #2f2430;
  background: #f8f4f0;
  font-family: "PingFang SC", sans-serif;
}
```

Create `sitemap.json`:

```json
{
  "desc": "DIY 手串定制商城页面索引",
  "rules": []
}
```

Create `pages/home/index.ts`:

```ts
/**
 * 小程序首页。
 *
 * 当前展示工程状态；商品目录计划落地后，本页将通过公开目录接口加载真实数据。
 */
Page({
  data: {
    title: 'DIY 手串定制商城',
    summary: '选珠 · DIY · 保存 · 下单 · 生产',
  },
})
```

Create `pages/home/index.json`:

```json
{
  "navigationBarTitleText": "DIY 手串"
}
```

Create `pages/home/index.wxml`:

```xml
<view class="page-shell">
  <text class="eyebrow">DIY BRACELET</text>
  <text class="title">{{ title }}</text>
  <text class="summary">{{ summary }}</text>
  <view class="card">
    <text class="card-title">工程基础已就绪</text>
    <text class="card-copy">下一阶段将接入真实商品目录和珠子素材。</text>
  </view>
</view>
```

Create `pages/home/index.wxss`:

```css
.page-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding: 96rpx 48rpx;
  box-sizing: border-box;
}

.eyebrow {
  color: #8a5c69;
  font-size: 24rpx;
  font-weight: 700;
  letter-spacing: 6rpx;
}

.title {
  margin-top: 24rpx;
  font-size: 54rpx;
  font-weight: 700;
}

.summary {
  margin-top: 24rpx;
  color: #6e6268;
  font-size: 30rpx;
}

.card {
  margin-top: 72rpx;
  padding: 40rpx;
  border: 1rpx solid #e4d7d2;
  border-radius: 28rpx;
  background: #fff;
}

.card-title,
.card-copy {
  display: block;
}

.card-title {
  font-size: 34rpx;
  font-weight: 700;
}

.card-copy {
  margin-top: 18rpx;
  color: #6e6268;
  line-height: 1.7;
}
```

- [ ] **Step 6: Run mini program logic tests**

Run:

```bash
cd miniprogram
pnpm test
```

Expected: one passing Jest test.

- [ ] **Step 7: Commit**

```bash
git add miniprogram
git commit -m "feat: scaffold native WeChat mini program"
```

### Task 7: Add local infrastructure and whole-repository verification

**Files:**

- Create: `docker-compose.yml`
- Create: `scripts/verify.sh`
- Modify: `docs/setup.md`
- Modify: `docs/testing.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Add MySQL and Redis Compose services**

Create `docker-compose.yml`:

```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: ${MYSQL_DATABASE:-diy_bracelet}
      MYSQL_USER: ${MYSQL_USER:-diy_bracelet}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-change-me}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-change-root-password}
    ports:
      - "${MYSQL_PORT:-3306}:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-p${MYSQL_ROOT_PASSWORD:-change-root-password}"]
      interval: 5s
      timeout: 3s
      retries: 20

  redis:
    image: redis:7-alpine
    ports:
      - "${REDIS_PORT:-6379}:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 20

volumes:
  mysql_data:
  redis_data:
```

- [ ] **Step 2: Add a single verification command**

Create `scripts/verify.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "[1/3] 后端测试"
(cd "${project_root}/backend" && mvn test)

echo "[2/3] 管理后台测试与构建"
(cd "${project_root}/admin-web" && pnpm test && pnpm build)

echo "[3/3] 小程序逻辑测试"
(cd "${project_root}/miniprogram" && pnpm test)

echo "全仓验证通过。"
```

Run:

```bash
chmod +x scripts/verify.sh
./scripts/verify.sh
```

Expected: backend tests, administration tests/build, and mini program tests all pass.

- [ ] **Step 3: Verify local infrastructure**

Run:

```bash
cp .env.example .env
docker compose config
docker compose up -d
docker compose ps
```

Expected: `mysql` and `redis` both become healthy.

- [ ] **Step 4: Update current documentation**

Append to `docs/setup.md`:

```markdown
## 验证基础设施

```bash
docker compose ps
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/public/project-health
```

两个后端接口均应返回 HTTP 200。
```

Append to `docs/testing.md`:

```markdown
## 工程基础阶段基线

- 后端健康接口 MockMvc 测试
- 后端统一业务异常契约测试
- 管理后台项目状态组件测试与生产构建
- 小程序业务摘要纯函数测试
```

Update `CHANGELOG.md` `Unreleased` section to:

```markdown
## Unreleased

- 建立项目设计规格和工程基础实施计划。
- 建立三端可启动工程骨架。
- 增加 MySQL、Redis 本地基础设施与全仓验证脚本。
```

- [ ] **Step 5: Run final verification**

Run:

```bash
./scripts/check-prerequisites.sh
./scripts/verify.sh
git diff --check
git status --short
```

Expected: prerequisite and repository verification pass; `git diff --check` emits no output; only intended Task 7 files are modified.

- [ ] **Step 6: Commit**

```bash
git add docker-compose.yml scripts/verify.sh docs/setup.md docs/testing.md CHANGELOG.md
git commit -m "build: add local infrastructure and repository verification"
```

## Plan completion criteria

- `README.md` and all current-version documents exist;
- JDK 17, Maven, Node, pnpm and Docker prerequisite check passes;
- Spring Boot health and error-contract tests pass;
- Vue administration shell test and production build pass;
- mini program logic test passes and project imports into WeChat Developer Tools;
- MySQL and Redis Compose services are healthy;
- `./scripts/verify.sh` passes;
- Git history contains focused commits for documentation, prerequisites, backend, admin web, mini program and infrastructure.

After this plan, write the next plan for the real catalog vertical slice: database migrations, local asset storage, SKU/package APIs, Vue catalog management, and mini program catalog browsing.
