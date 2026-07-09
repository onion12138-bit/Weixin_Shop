# DIY 编辑器、权威计价与方案草稿 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付匿名访客可使用的 DIY 排珠、尺寸校验、权威计价、方案管理和预览图闭环，并提供对应的管理后台配置能力。

**Architecture:** 后端新增 `identity` 与 `diy` 两个业务模块：访客 JWT 只负责身份，DIY 应用服务分别负责配置、计价和方案聚合。小程序把编辑状态、历史记录、坐标计算与 Canvas 页面分离；管理后台沿用现有 Vue 单页结构增加 DIY 配置工作区。对象存储继续通过现有 `FileStorage` 端口隔离。

**Tech Stack:** JDK 17、Spring Boot 3.2.12、Spring Security、MyBatis-Plus 3.5.7、Flyway、MySQL 8、JUnit 5、Testcontainers、Vue 3.5、Vitest、原生微信小程序、TypeScript 5.7、Jest。

---

## 1. 文件与职责映射

### 后端

- `backend/src/main/resources/db/migration/V3__diy_identity_pricing_schema.sql`：访客、DIY 配置、计价和方案表。
- `backend/src/main/resources/db/migration/V4__diy_seed.sql`：默认尺寸、工艺费和绳材。
- `backend/src/main/java/com/diybracelet/mall/identity/**`：访客身份交换、摘要、顾客 JWT、过滤器和身份解析端口。
- `backend/src/main/java/com/diybracelet/mall/diy/domain/**`：DIY 配置、绳材、方案、报价和稳定枚举。
- `backend/src/main/java/com/diybracelet/mall/diy/infrastructure/mapper/**`：DIY 表 Mapper 与乐观锁 SQL。
- `backend/src/main/java/com/diybracelet/mall/diy/application/DiyConfigurationService.java`：配置查询和管理写入。
- `backend/src/main/java/com/diybracelet/mall/diy/application/DiyPricingService.java`：尺寸、SKU、费用和报价指纹。
- `backend/src/main/java/com/diybracelet/mall/diy/application/DiySchemeService.java`：方案所有权、保存、复制、删除和预览绑定。
- `backend/src/main/java/com/diybracelet/mall/diy/api/**`：顾客与管理 HTTP 契约。

### 管理后台

- `admin-web/src/api/diy.ts`：DIY 管理接口和类型。
- `admin-web/src/views/DiySettingsView.vue`：尺寸、工艺费、绳材三个配置面板。
- `admin-web/src/views/DiySettingsView.spec.ts`：加载、校验和保存组件测试。
- `admin-web/src/App.vue`：在现有侧栏中切换商品目录与 DIY 设置。
- `admin-web/src/style.css`：配置表格、区间编辑和状态样式。

### 微信小程序

- `miniprogram/miniprogram/domain/diy-types.ts`：共享的编辑器类型。
- `miniprogram/miniprogram/domain/editor-state.ts`：SKU 顺序数组的不可变编辑命令。
- `miniprogram/miniprogram/domain/editor-history.ts`：50 步撤销和重做。
- `miniprogram/miniprogram/domain/bracelet-layout.ts`：环形坐标计算。
- `miniprogram/miniprogram/domain/price-estimate.ts`：整数分预估和 SKU 分组。
- `miniprogram/miniprogram/services/session.ts`：安装 UUID、访客令牌和一次续期。
- `miniprogram/miniprogram/services/diy.ts`：配置、报价、方案和预览接口。
- `miniprogram/miniprogram/services/request.ts`：附加顾客令牌和 401 单次重试。
- `miniprogram/miniprogram/pages/diy/editor.*`：Canvas 工作台。
- `miniprogram/miniprogram/pages/diy/schemes.*`：方案列表与恢复入口。
- `miniprogram/src/*diy*.spec.ts`：纯状态、布局、计价和会话测试。

### 验证与文档

- `scripts/diy-smoke.sh`：真实 HTTP 闭环。
- `scripts/verify.sh`：继续作为全仓门禁。
- `docs/*.md`、`CHANGELOG.md`：记录实际能力、限制和验证证据。

## 2. 实施任务

### Task 1: 创建 DIY 数据库结构与默认配置

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__diy_identity_pricing_schema.sql`
- Create: `backend/src/main/resources/db/migration/V4__diy_seed.sql`
- Create: `backend/src/test/java/com/diybracelet/mall/diy/infrastructure/DiyMigrationTest.java`

- [ ] **Step 1: 编写失败的迁移测试**

```java
package com.diybracelet.mall.diy.infrastructure;

import com.diybracelet.mall.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class DiyMigrationTest extends MySqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateDiySchemaAndSeedUsableDefaults() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = database()
                  and table_name in (
                    'customer_identity',
                    'diy_rule_config',
                    'craft_fee_rule',
                    'cord_option',
                    'diy_scheme'
                  )
                """, Integer.class);
        Integer maxBeads = jdbcTemplate.queryForObject(
                "select max_bead_count from diy_rule_config where id = 1",
                Integer.class
        );
        Integer enabledCords = jdbcTemplate.queryForObject(
                "select count(*) from cord_option where status = 'ENABLED'",
                Integer.class
        );

        assertThat(tableCount).isEqualTo(5);
        assertThat(maxBeads).isEqualTo(60);
        assertThat(enabledCords).isEqualTo(3);
    }
}
```

- [ ] **Step 2: 运行测试并确认因表不存在而失败**

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn -Dtest=DiyMigrationTest test
```

Expected: FAIL，日志包含 `Table 'diy_rule_config' doesn't exist` 或迁移测试断言为 `0`。

- [ ] **Step 3: 创建 V3 迁移**

```sql
-- 匿名访客、DIY 配置、计价和方案结构。
create table customer_identity (
    id bigint primary key auto_increment,
    source varchar(32) not null,
    external_key_hash char(64) not null,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    unique key uk_customer_identity_source_key (source, external_key_hash),
    constraint chk_customer_identity_source check (source in ('GUEST', 'WECHAT')),
    constraint chk_customer_identity_status check (status in ('ACTIVE', 'DISABLED'))
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table diy_rule_config (
    id bigint primary key,
    min_wrist_mm decimal(6,2) not null,
    max_wrist_mm decimal(6,2) not null,
    snug_allowance_mm decimal(6,2) not null,
    standard_allowance_mm decimal(6,2) not null,
    loose_allowance_mm decimal(6,2) not null,
    tolerance_mm decimal(6,2) not null,
    max_bead_count int not null,
    config_version bigint not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint chk_diy_wrist_range check (
        min_wrist_mm > 0 and max_wrist_mm > min_wrist_mm
    ),
    constraint chk_diy_allowances check (
        snug_allowance_mm >= 0
        and standard_allowance_mm >= 0
        and loose_allowance_mm >= 0
        and tolerance_mm >= 0
    ),
    constraint chk_diy_max_beads check (
        max_bead_count between 1 and 200
    )
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table craft_fee_rule (
    id bigint primary key auto_increment,
    min_bead_count int not null,
    max_bead_count int not null,
    fee decimal(12,2) not null,
    status varchar(32) not null,
    sort_order int not null,
    rule_version bigint not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint chk_craft_count_range check (
        min_bead_count > 0 and max_bead_count >= min_bead_count
    ),
    constraint chk_craft_fee_non_negative check (fee >= 0),
    constraint chk_craft_status check (status in ('ENABLED', 'DISABLED')),
    index idx_craft_fee_match (status, min_bead_count, max_bead_count)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table cord_option (
    id bigint primary key auto_increment,
    code varchar(64) not null unique,
    name varchar(80) not null,
    color_hex char(7) not null,
    extra_fee decimal(12,2) not null,
    status varchar(32) not null,
    sort_order int not null,
    option_version bigint not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint chk_cord_fee_non_negative check (extra_fee >= 0),
    constraint chk_cord_status check (status in ('ENABLED', 'DISABLED')),
    index idx_cord_status_sort (status, sort_order)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;

create table diy_scheme (
    id bigint primary key auto_increment,
    customer_identity_id bigint not null,
    name varchar(80) not null,
    wrist_circumference_mm decimal(6,2) not null,
    fit_type varchar(32) not null,
    cord_option_id bigint not null,
    sequence_json json not null,
    bead_count int not null,
    catalog_snapshot_json json not null,
    bead_amount decimal(12,2) null,
    craft_fee decimal(12,2) null,
    cord_fee decimal(12,2) null,
    total_amount decimal(12,2) null,
    quote_fingerprint char(64) null,
    quote_status varchar(32) not null,
    preview_asset_id bigint null,
    status varchar(32) not null,
    version bigint not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_scheme_customer
        foreign key (customer_identity_id) references customer_identity(id),
    constraint fk_scheme_cord
        foreign key (cord_option_id) references cord_option(id),
    constraint fk_scheme_preview
        foreign key (preview_asset_id) references asset_file(id),
    constraint chk_scheme_wrist_positive check (wrist_circumference_mm > 0),
    constraint chk_scheme_bead_count check (bead_count between 0 and 200),
    constraint chk_scheme_fit_type check (
        fit_type in ('SNUG', 'STANDARD', 'LOOSE')
    ),
    constraint chk_scheme_quote_status check (
        quote_status in ('NOT_QUOTED', 'VALID', 'STALE', 'INVALID')
    ),
    constraint chk_scheme_status check (status in ('ACTIVE', 'DELETED')),
    index idx_scheme_customer_status_updated (
        customer_identity_id,
        status,
        updated_at
    )
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_0900_ai_ci;
```

- [ ] **Step 4: 创建 V4 默认配置**

```sql
insert into diy_rule_config (
    id,
    min_wrist_mm,
    max_wrist_mm,
    snug_allowance_mm,
    standard_allowance_mm,
    loose_allowance_mm,
    tolerance_mm,
    max_bead_count,
    config_version
) values (1, 120.00, 250.00, 5.00, 10.00, 15.00, 4.00, 60, 1);

insert into craft_fee_rule (
    id,
    min_bead_count,
    max_bead_count,
    fee,
    status,
    sort_order,
    rule_version
) values (5001, 1, 60, 10.00, 'ENABLED', 10, 1);

insert into cord_option (
    id,
    code,
    name,
    color_hex,
    extra_fee,
    status,
    sort_order,
    option_version
) values
    (6001, 'ELASTIC-CLEAR', '透明弹力绳', '#E8E4DC', 0.00, 'ENABLED', 10, 1),
    (6002, 'ELASTIC-BLACK', '黑色弹力绳', '#272523', 0.00, 'ENABLED', 20, 1),
    (6003, 'ELASTIC-BROWN', '棕色弹力绳', '#72513C', 0.00, 'ENABLED', 30, 1);
```

- [ ] **Step 5: 运行迁移测试**

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn -Dtest=DiyMigrationTest test
```

Expected: PASS，`Tests run: 1, Failures: 0, Errors: 0`。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/resources/db/migration/V3__diy_identity_pricing_schema.sql \
  backend/src/main/resources/db/migration/V4__diy_seed.sql \
  backend/src/test/java/com/diybracelet/mall/diy/infrastructure/DiyMigrationTest.java
git commit -m "feat: add DIY database schema"
```

### Task 2: 实现匿名访客身份和顾客 JWT

**Files:**
- Create: `backend/src/main/java/com/diybracelet/mall/identity/api/GuestSessionController.java`
- Create: `backend/src/main/java/com/diybracelet/mall/identity/api/dto/GuestSessionRequest.java`
- Create: `backend/src/main/java/com/diybracelet/mall/identity/api/dto/GuestSessionResponse.java`
- Create: `backend/src/main/java/com/diybracelet/mall/identity/application/GuestIdentityService.java`
- Create: `backend/src/main/java/com/diybracelet/mall/identity/domain/CustomerIdentity.java`
- Create: `backend/src/main/java/com/diybracelet/mall/identity/domain/CustomerPrincipal.java`
- Create: `backend/src/main/java/com/diybracelet/mall/identity/domain/CustomerIdentityResolver.java`
- Create: `backend/src/main/java/com/diybracelet/mall/identity/infrastructure/CustomerIdentityMapper.java`
- Create: `backend/src/main/java/com/diybracelet/mall/identity/infrastructure/CustomerJwtService.java`
- Create: `backend/src/main/java/com/diybracelet/mall/identity/infrastructure/CustomerJwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/diybracelet/mall/identity/infrastructure/SecurityCustomerIdentityResolver.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/auth/infrastructure/JwtService.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/auth/infrastructure/SecurityConfiguration.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `.env.example`
- Create: `backend/src/test/java/com/diybracelet/mall/identity/api/GuestSessionControllerTest.java`
- Modify: existing MVC test property declarations that import `SecurityConfiguration`

- [ ] **Step 1: 编写失败的访客会话集成测试**

```java
package com.diybracelet.mall.identity.api;

import com.diybracelet.mall.identity.infrastructure.CustomerJwtService;
import com.diybracelet.mall.support.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.customer-auth.jwt-secret=customer-test-secret-0123456789012345",
        "app.customer-auth.jwt-ttl=P30D",
        "app.customer-auth.jwt-issuer=diy-customer-test",
        "app.customer-auth.identity-pepper=identity-test-pepper-0123456789012"
})
class GuestSessionControllerTest extends MySqlIntegrationTest {

    private static final String INSTALLATION_ID =
            "3f6f7816-0f1a-4aa0-870f-f34f9195a2ec";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerJwtService customerJwtService;

    @Test
    void shouldReuseGuestIdentityAndIssueCustomerToken() throws Exception {
        String body = """
                {"installationId":"%s"}
                """.formatted(INSTALLATION_ID);

        String first = mockMvc.perform(post("/api/app/session/guest")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(post("/api/app/session/guest")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from customer_identity",
                Integer.class
        );
        assertThat(count).isEqualTo(1);
        assertThat(first).doesNotContain(INSTALLATION_ID);
    }

    @Test
    void shouldProtectCustomerApiWithoutCustomerToken() throws Exception {
        mockMvc.perform(get("/api/app/diy/config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_UNAUTHORIZED"));
    }
}
```

- [ ] **Step 2: 运行测试并确认编译失败**

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn -Dtest=GuestSessionControllerTest test
```

Expected: FAIL，编译器报告 `CustomerJwtService` 或访客 Controller 不存在。

- [ ] **Step 3: 创建访客领域类型和请求契约**

```java
public record CustomerPrincipal(Long customerIdentityId) {
    public CustomerPrincipal {
        if (customerIdentityId == null || customerIdentityId <= 0) {
            throw new IllegalArgumentException("顾客身份主键必须大于零");
        }
    }
}

public interface CustomerIdentityResolver {
    Long requireCustomerIdentityId();
}

public record GuestSessionRequest(
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
        )
        String installationId
) {
}

public record GuestSessionResponse(
        Long customerIdentityId,
        String accessToken,
        long expiresInSeconds
) {
}
```

`CustomerIdentity` 使用 `@TableName("customer_identity")`，字段固定为
`id/source/externalKeyHash/status/createdAt/updatedAt`，提供 MyBatis 所需无参构造器和完整 getter/setter。
`CustomerIdentityMapper` 继承 `BaseMapper<CustomerIdentity>`。

- [ ] **Step 4: 实现摘要、幂等身份创建和顾客 JWT**

`GuestIdentityService.exchange` 的核心实现必须是：

```java
@Transactional
public GuestSessionResponse exchange(String installationId) {
    String externalKeyHash = sha256Hex(
            installationId.trim().toLowerCase(Locale.ROOT),
            identityPepper
    );
    CustomerIdentity identity = mapper.selectOne(
            new LambdaQueryWrapper<CustomerIdentity>()
                    .eq(CustomerIdentity::getSource, "GUEST")
                    .eq(CustomerIdentity::getExternalKeyHash, externalKeyHash)
    );
    if (identity == null) {
        identity = new CustomerIdentity();
        identity.setSource("GUEST");
        identity.setExternalKeyHash(externalKeyHash);
        identity.setStatus("ACTIVE");
        try {
            mapper.insert(identity);
        } catch (DuplicateKeyException exception) {
            identity = mapper.selectOne(
                    new LambdaQueryWrapper<CustomerIdentity>()
                            .eq(CustomerIdentity::getSource, "GUEST")
                            .eq(CustomerIdentity::getExternalKeyHash, externalKeyHash)
            );
        }
    }
    if (!"ACTIVE".equals(identity.getStatus())) {
        throw new BusinessException(
                ErrorCode.COMMON_FORBIDDEN,
                "访客身份已停用"
        );
    }
    return new GuestSessionResponse(
            identity.getId(),
            customerJwtService.issue(identity.getId()),
            customerJwtService.expiresInSeconds()
    );
}
```

摘要使用 `MessageDigest.getInstance("SHA-256")` 计算
`pepper + ":" + normalizedInstallationId` 的 UTF-8 十六进制结果。构造器拒绝少于 32 字节的 pepper。

`CustomerJwtService` 使用独立配置，签发以下声明：

```java
return Jwts.builder()
        .subject(customerIdentityId.toString())
        .issuer(issuer)
        .issuedAt(Date.from(issuedAt))
        .expiration(Date.from(expiresAt))
        .claim("tokenType", "CUSTOMER")
        .claim("roles", List.of("ROLE_CUSTOMER"))
        .signWith(signingKey)
        .compact();
```

解析时必须同时 `requireIssuer(issuer)` 并检查 `tokenType` 为 `CUSTOMER`。
现有管理员 `JwtService` 增加 `tokenType=ADMIN`，`parseUsername` 拒绝其他类型。

- [ ] **Step 5: 接入 Spring Security**

`CustomerJwtAuthenticationFilter` 成功解析后把 `CustomerPrincipal` 放入安全上下文，
权限固定为 `ROLE_CUSTOMER`。`SecurityCustomerIdentityResolver` 从安全上下文读取该 principal，
不存在时抛 `COMMON_UNAUTHORIZED`。

`SecurityConfiguration` 的规则顺序调整为：

```java
.authorizeHttpRequests(authorize -> authorize
        .requestMatchers(
                "/api/public/**",
                "/api/admin/auth/login",
                "/api/app/session/guest",
                "/actuator/health"
        ).permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/app/**").hasRole("CUSTOMER")
        .anyRequest().authenticated()
)
.addFilterBefore(
        customerJwtAuthenticationFilter,
        JwtAuthenticationFilter.class
)
.addFilterBefore(
        jwtAuthenticationFilter,
        UsernamePasswordAuthenticationFilter.class
);
```

`application.yml` 和 `.env.example` 增加：

```yaml
app:
  customer-auth:
    jwt-secret: ${CUSTOMER_JWT_SECRET:local-customer-secret-change-me-32-bytes}
    jwt-ttl: ${CUSTOMER_JWT_TTL:P30D}
    jwt-issuer: ${CUSTOMER_JWT_ISSUER:diy-bracelet-customer}
    identity-pepper: ${CUSTOMER_IDENTITY_PEPPER:local-identity-pepper-change-me-32-bytes}
```

现有加载安全配置的 `@WebMvcTest` 增加四个 `app.customer-auth.*` 测试属性，
属性值使用 Task 2 测试中的固定 32 字节以上测试密钥。

- [ ] **Step 6: 实现访客 Controller 并运行身份测试**

```java
@RestController
@RequestMapping("/api/app/session")
public class GuestSessionController {

    private final GuestIdentityService guestIdentityService;

    public GuestSessionController(GuestIdentityService guestIdentityService) {
        this.guestIdentityService = guestIdentityService;
    }

    @PostMapping("/guest")
    public ApiResponse<GuestSessionResponse> guest(
            @Valid @RequestBody GuestSessionRequest request
    ) {
        return ApiResponse.success(
                guestIdentityService.exchange(request.installationId()),
                UUID.randomUUID().toString()
        );
    }
}
```

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn \
  -Dtest=GuestSessionControllerTest,AdminAuthControllerTest,FileControllerTest \
  test
```

Expected: PASS，管理员和顾客令牌测试均无回归。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/diybracelet/mall/identity \
  backend/src/main/java/com/diybracelet/mall/auth/infrastructure \
  backend/src/main/resources/application.yml \
  backend/src/test/java \
  .env.example
git commit -m "feat: add guest customer identity"
```

### Task 3: 实现可配置的 DIY 尺寸、工艺费和绳材后端

**Files:**
- Create: `backend/src/main/java/com/diybracelet/mall/diy/domain/DiyRuleConfig.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/domain/CraftFeeRule.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/domain/CordOption.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/domain/OptionStatus.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/infrastructure/mapper/DiyRuleConfigMapper.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/infrastructure/mapper/CraftFeeRuleMapper.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/infrastructure/mapper/CordOptionMapper.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/api/dto/DiyConfigurationView.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/api/dto/AdminDiyConfigurationView.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/api/dto/SaveDiyRuleConfigRequest.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/api/dto/SaveCraftFeeRulesRequest.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/api/dto/SaveCordOptionRequest.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/application/DiyConfigurationService.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/api/AdminDiyConfigurationController.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/api/AppDiyConfigurationController.java`
- Create: `backend/src/test/java/com/diybracelet/mall/diy/api/DiyConfigurationControllerTest.java`

- [ ] **Step 1: 编写失败的配置接口集成测试**

测试通过访客会话取得顾客 token，通过 `JwtService.issueAccessToken("admin")` 取得管理员 token，
并断言：

```java
mockMvc.perform(get("/api/app/diy/config")
                .header("Authorization", "Bearer " + customerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rule.maxBeadCount").value(60))
        .andExpect(jsonPath("$.data.cordOptions.length()").value(3));

mockMvc.perform(put("/api/admin/diy/craft-fees")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content("""
                        {
                          "rules": [
                            {"minBeadCount":1,"maxBeadCount":20,"fee":8.00},
                            {"minBeadCount":20,"maxBeadCount":60,"fee":12.00}
                          ]
                        }
                        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("工艺费区间不能重叠"));
```

- [ ] **Step 2: 运行测试并确认接口不存在**

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn -Dtest=DiyConfigurationControllerTest test
```

Expected: FAIL，顾客配置请求为 404 或测试无法编译。

- [ ] **Step 3: 创建实体、Mapper 和 DTO**

三个实体遵循现有 `BeadSku` 写法，字段与 V3 完全一致。
`DiyConfigurationView` 的公开契约固定为：

```java
public record DiyConfigurationView(
        Rule rule,
        List<CraftFee> craftFeeRules,
        List<Cord> cordOptions
) {
    public record Rule(
            BigDecimal minWristMm,
            BigDecimal maxWristMm,
            BigDecimal snugAllowanceMm,
            BigDecimal standardAllowanceMm,
            BigDecimal looseAllowanceMm,
            BigDecimal toleranceMm,
            Integer maxBeadCount,
            Long configVersion
    ) {
    }

    public record CraftFee(
            Long id,
            Integer minBeadCount,
            Integer maxBeadCount,
            BigDecimal fee,
            Long ruleVersion
    ) {
    }

    public record Cord(
            Long id,
            String code,
            String name,
            String colorHex,
            BigDecimal extraFee,
            Long optionVersion
    ) {
    }
}
```

管理接口使用独立的 `AdminDiyConfigurationView`。它复用相同的 Rule 和 CraftFee
字段，但 Cord 记录完整管理字段：

```java
public record AdminDiyConfigurationView(
        DiyConfigurationView.Rule rule,
        List<DiyConfigurationView.CraftFee> craftFeeRules,
        List<Cord> cordOptions
) {
    public record Cord(
            Long id,
            String code,
            String name,
            String colorHex,
            BigDecimal extraFee,
            OptionStatus status,
            Integer sortOrder,
            Long optionVersion
    ) {
    }
}
```

`SaveDiyRuleConfigRequest` 对金额字段使用 `@DecimalMin("0.00")`，
对 `maxBeadCount` 使用 `@Min(1) @Max(200)`。
`SaveCordOptionRequest` 对 code 使用 `^[A-Z0-9-]{2,64}$`，
对颜色使用 `^#[0-9A-Fa-f]{6}$`。
工艺费请求把输入类型定义为嵌套 record，避免另建无业务职责的文件：

```java
public record SaveCraftFeeRulesRequest(
        @NotEmpty List<@Valid CraftFeeRuleInput> rules
) {
    public record CraftFeeRuleInput(
            @NotNull @Min(1) Integer minBeadCount,
            @NotNull @Min(1) Integer maxBeadCount,
            @NotNull @DecimalMin("0.00") BigDecimal fee
    ) {
    }
}
```

服务方法统一使用
`List<SaveCraftFeeRulesRequest.CraftFeeRuleInput>`，包括排序与完整覆盖校验。

- [ ] **Step 4: 实现配置服务的完整校验**

`DiyConfigurationService.replaceCraftFeeRules` 在事务中先验证，再删除旧规则并写入新规则：

```java
private void validateCraftFeeRules(
        List<SaveCraftFeeRulesRequest.CraftFeeRuleInput> rules,
        int maxBeadCount
) {
    if (rules == null || rules.isEmpty()) {
        throw new BusinessException(
                ErrorCode.COMMON_BAD_REQUEST,
                "工艺费规则不能为空"
        );
    }
    List<SaveCraftFeeRulesRequest.CraftFeeRuleInput> sorted = rules.stream()
            .sorted(Comparator.comparing(
                    SaveCraftFeeRulesRequest.CraftFeeRuleInput::minBeadCount
            ))
            .toList();
    int expectedMin = 1;
    for (SaveCraftFeeRulesRequest.CraftFeeRuleInput rule : sorted) {
        if (rule.minBeadCount() < expectedMin) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "工艺费区间不能重叠"
            );
        }
        if (rule.minBeadCount() > expectedMin) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "工艺费区间不能留空档"
            );
        }
        if (rule.maxBeadCount() < rule.minBeadCount()) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    "工艺费区间结束值不能小于开始值"
            );
        }
        expectedMin = rule.maxBeadCount() + 1;
    }
    if (expectedMin != maxBeadCount + 1) {
        throw new BusinessException(
                ErrorCode.COMMON_BAD_REQUEST,
                "工艺费区间必须覆盖 1 到最大珠数"
        );
    }
}
```

规则配置保存时检查 `minWristMm < maxWristMm`，成功后 `configVersion + 1`。
工艺费整体替换使用同一个递增 `ruleVersion`。
绳材更新时 `optionVersion + 1`，已有方案引用的绳材只能停用，不能物理删除。

- [ ] **Step 5: 实现顾客和管理 Controller**

```java
@GetMapping("/config")
public ApiResponse<DiyConfigurationView> appConfiguration() {
    return success(service.getPublicConfiguration());
}

@PutMapping("/config")
public ApiResponse<DiyConfigurationView> saveRule(
        @Valid @RequestBody SaveDiyRuleConfigRequest request
) {
    return success(service.saveRule(request));
}

@PutMapping("/craft-fees")
public ApiResponse<DiyConfigurationView> replaceCraftFees(
        @Valid @RequestBody SaveCraftFeeRulesRequest request
) {
    return success(service.replaceCraftFeeRules(request.rules()));
}
```

顾客 Controller 映射 `/api/app/diy`，管理 Controller 映射 `/api/admin/diy`。
顾客响应只返回启用绳材；管理响应返回全部绳材和状态。

- [ ] **Step 6: 运行配置测试**

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn \
  -Dtest=DiyConfigurationControllerTest,GuestSessionControllerTest \
  test
```

Expected: PASS，重叠和缺口均返回 400，合法整体替换后配置版本递增。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/diybracelet/mall/diy \
  backend/src/test/java/com/diybracelet/mall/diy/api/DiyConfigurationControllerTest.java
git commit -m "feat: add configurable DIY rules"
```

### Task 4: 实现尺寸评估和权威计价

**Files:**
- Create: `backend/src/main/java/com/diybracelet/mall/diy/domain/FitType.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/domain/SizeStatus.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/domain/DiyValidationIssue.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/domain/DiyDesignCommand.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/domain/DiyEvaluation.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/api/dto/DiyQuoteRequest.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/api/dto/DiyQuoteResponse.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/application/DiyPricingService.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/api/AppDiyPricingController.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/catalog/infrastructure/mapper/BeadSkuMapper.java`
- Create: `backend/src/test/java/com/diybracelet/mall/diy/api/AppDiyPricingControllerTest.java`

- [ ] **Step 1: 编写失败的权威报价集成测试**

用真实 MySQL 演示 SKU 构造 21 颗、168 mm 的标准腕围方案：

```java
List<Long> sequence = new ArrayList<>();
sequence.addAll(Collections.nCopies(12, 2001L));
sequence.addAll(Collections.nCopies(9, 2002L));

mockMvc.perform(post("/api/app/diy/quotes")
                .header("Authorization", "Bearer " + customerToken)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "wristCircumferenceMm", 160,
                        "fitType", "STANDARD",
                        "cordOptionId", 6001,
                        "sequence", sequence
                ))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.quotable").value(true))
        .andExpect(jsonPath("$.data.currentLengthMm").value(168.00))
        .andExpect(jsonPath("$.data.targetLengthMm").value(170.00))
        .andExpect(jsonPath("$.data.sizeStatus").value("FIT"))
        .andExpect(jsonPath("$.data.beadAmount").value(304.80))
        .andExpect(jsonPath("$.data.craftFee").value(10.00))
        .andExpect(jsonPath("$.data.totalAmount").value(314.80))
        .andExpect(jsonPath("$.data.quoteFingerprint").isNotEmpty());
```

再增加一个测试：把 `2002` 改为不存在的 `999999`，响应仍为 200，
但 `quotable=false` 且 issues 含 `SKU_UNAVAILABLE`，避免把业务可修复状态混成网络错误。

- [ ] **Step 2: 运行测试并确认报价接口不存在**

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn -Dtest=AppDiyPricingControllerTest test
```

Expected: FAIL，`/api/app/diy/quotes` 返回 404。

- [ ] **Step 3: 创建计价输入输出类型**

```java
public record DiyQuoteRequest(
        @NotNull @DecimalMin("0.01") BigDecimal wristCircumferenceMm,
        @NotNull FitType fitType,
        @NotNull @Positive Long cordOptionId,
        @NotNull @Size(max = 200) List<@Positive Long> sequence
) {
}

public record DiyEvaluation(
        boolean quotable,
        List<DiyValidationIssue> issues,
        Map<Long, Integer> skuQuantities,
        BigDecimal currentLengthMm,
        BigDecimal targetLengthMm,
        BigDecimal deviationMm,
        SizeStatus sizeStatus,
        BigDecimal beadAmount,
        BigDecimal craftFee,
        BigDecimal cordFee,
        BigDecimal totalAmount,
        String quoteFingerprint
) {
}

public record DiyDesignCommand(
        BigDecimal wristCircumferenceMm,
        FitType fitType,
        Long cordOptionId,
        List<Long> sequence
) {
    public DiyDesignCommand {
        sequence = List.copyOf(sequence);
    }
}
```

`DiyValidationIssue` 是 `record(code, message, skuId)`；
错误码固定使用 `EMPTY_SEQUENCE`、`WRIST_OUT_OF_RANGE`、`BEAD_COUNT_EXCEEDED`、
`SKU_UNAVAILABLE`、`CORD_UNAVAILABLE`、`SIZE_TOO_SHORT`、`SIZE_TOO_LONG`、
`CRAFT_FEE_NOT_CONFIGURED`。
`DiyQuoteRequest.toCommand()` 只执行字段映射：

```java
public DiyDesignCommand toCommand() {
    return new DiyDesignCommand(
            wristCircumferenceMm,
            fitType,
            cordOptionId,
            sequence
    );
}
```

- [ ] **Step 4: 增加可售 SKU 批量查询**

在 `BeadSkuMapper` 增加：

```java
@Select("""
        <script>
        select bead.*
        from bead_sku bead
        inner join category category
          on category.id = bead.category_id
         and category.status = 'ON_SALE'
        where bead.status = 'ON_SALE'
          and bead.id in
          <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
          </foreach>
        </script>
        """)
List<BeadSku> selectSellableForDiy(@Param("ids") List<Long> ids);
```

当 IDs 为空时，计价服务不调用此 SQL。

- [ ] **Step 5: 实现计价算法**

`DiyPricingService.evaluate` 必须：

1. 用 `LinkedHashMap<Long, Integer>` 从 sequence 派生数量；
2. 一次查询全部可售 SKU；
3. 当前长度使用 `diameterMm × count` 求和；
4. 目标长度使用腕围加 `FitType` 对应余量；
5. 根据容差返回 `FIT/TOO_SHORT/TOO_LONG`；
6. 只在无 issue 时计算金额和指纹；
7. 所有金额执行 `setScale(2, RoundingMode.HALF_UP)`。

核心金额代码：

```java
BigDecimal beadAmount = quantities.entrySet().stream()
        .map(entry -> beadsById.get(entry.getKey())
                .getSalePrice()
                .multiply(BigDecimal.valueOf(entry.getValue())))
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(2, RoundingMode.HALF_UP);

BigDecimal totalAmount = beadAmount
        .add(craftFee.getFee())
        .add(cordOption.getExtraFee())
        .setScale(2, RoundingMode.HALF_UP);
```

指纹使用 `SHA-256`，规范字符串按 SKU ID 升序拼接：

```java
String canonical = "config=" + config.getConfigVersion()
        + "|cord=" + cordOption.getId()
        + ":" + cordOption.getOptionVersion()
        + "|craft=" + craftFee.getId()
        + ":" + craftFee.getRuleVersion()
        + quantities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "|sku="
                        + entry.getKey()
                        + ":"
                        + entry.getValue()
                        + ":"
                        + beadsById.get(entry.getKey())
                                .getSalePrice()
                                .setScale(2, RoundingMode.HALF_UP)
                                .toPlainString())
                .collect(Collectors.joining());
```

- [ ] **Step 6: 实现报价 Controller 并运行测试**

```java
@PostMapping("/quotes")
public ApiResponse<DiyQuoteResponse> quote(
        @Valid @RequestBody DiyQuoteRequest request
) {
    return success(DiyQuoteResponse.from(
            pricingService.evaluate(request.toCommand())
    ));
}
```

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn \
  -Dtest=AppDiyPricingControllerTest,DiyConfigurationControllerTest \
  test
```

Expected: PASS，合法方案为 `quotable=true`，不可售 SKU 返回可修复 issue。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/diybracelet/mall/diy \
  backend/src/main/java/com/diybracelet/mall/catalog/infrastructure/mapper/BeadSkuMapper.java \
  backend/src/test/java/com/diybracelet/mall/diy/api/AppDiyPricingControllerTest.java
git commit -m "feat: add authoritative DIY pricing"
```

### Task 5: 实现方案保存、恢复、复制、删除和乐观锁

**Files:**
- Create: `backend/src/main/java/com/diybracelet/mall/diy/domain/DiyScheme.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/domain/QuoteStatus.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/domain/SchemeStatus.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/infrastructure/mapper/DiySchemeMapper.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/api/dto/SaveDiySchemeRequest.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/api/dto/DiySchemeResponse.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/application/DiySchemeService.java`
- Create: `backend/src/main/java/com/diybracelet/mall/diy/api/AppDiySchemeController.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/shared/error/ErrorCode.java`
- Create: `backend/src/test/java/com/diybracelet/mall/diy/api/AppDiySchemeControllerTest.java`

- [ ] **Step 1: 编写失败的方案闭环集成测试**

测试按顺序执行：

```java
Long schemeId = createScheme(customerToken, "我的第一串", 0L);

mockMvc.perform(get("/api/app/diy/schemes/{id}", schemeId)
                .header("Authorization", "Bearer " + customerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sequence[0]").value(2001))
        .andExpect(jsonPath("$.data.sequence[12]").value(2002))
        .andExpect(jsonPath("$.data.beadCount").value(21))
        .andExpect(jsonPath("$.data.quoteStatus").value("VALID"));

mockMvc.perform(put("/api/app/diy/schemes/{id}", schemeId)
                .header("Authorization", "Bearer " + customerToken)
                .contentType("application/json")
                .content(updatePayloadWithVersion(0)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.version").value(1));

mockMvc.perform(put("/api/app/diy/schemes/{id}", schemeId)
                .header("Authorization", "Bearer " + customerToken)
                .contentType("application/json")
                .content(updatePayloadWithVersion(0)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DIY_SCHEME_VERSION_CONFLICT"));
```

另建第二个访客 token，读取第一个访客方案必须返回 404。

- [ ] **Step 2: 运行测试并确认方案接口不存在**

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn -Dtest=AppDiySchemeControllerTest test
```

Expected: FAIL，方案 Controller 或 DTO 不存在。

- [ ] **Step 3: 创建方案实体和稳定错误码**

`DiyScheme` 字段与 V3 一一对应，`sequenceJson` 和 `catalogSnapshotJson` 为字符串，
金额允许 `null`，version 初始值为 0。

`ErrorCode` 增加：

```java
DIY_SCHEME_VERSION_CONFLICT(HttpStatus.CONFLICT),
DIY_DESIGN_NOT_QUOTABLE(HttpStatus.UNPROCESSABLE_ENTITY);
```

`SaveDiySchemeRequest`：

```java
public record SaveDiySchemeRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull @DecimalMin("0.01") BigDecimal wristCircumferenceMm,
        @NotNull FitType fitType,
        @NotNull @Positive Long cordOptionId,
        @NotNull @Size(max = 200) List<@Positive Long> sequence,
        @NotNull @Min(0) Long version
) {
}
```

创建接口忽略请求 version 并使用 0；更新接口必须匹配当前 version。

- [ ] **Step 4: 实现所有权和乐观锁 Mapper**

```java
@Update("""
        update diy_scheme
        set name = #{scheme.name},
            wrist_circumference_mm = #{scheme.wristCircumferenceMm},
            fit_type = #{scheme.fitType},
            cord_option_id = #{scheme.cordOptionId},
            sequence_json = #{scheme.sequenceJson},
            bead_count = #{scheme.beadCount},
            catalog_snapshot_json = #{scheme.catalogSnapshotJson},
            bead_amount = #{scheme.beadAmount},
            craft_fee = #{scheme.craftFee},
            cord_fee = #{scheme.cordFee},
            total_amount = #{scheme.totalAmount},
            quote_fingerprint = #{scheme.quoteFingerprint},
            quote_status = #{scheme.quoteStatus},
            version = version + 1
        where id = #{scheme.id}
          and customer_identity_id = #{customerId}
          and status = 'ACTIVE'
          and version = #{expectedVersion}
        """)
int updateOwnedWithVersion(
        @Param("scheme") DiyScheme scheme,
        @Param("customerId") Long customerId,
        @Param("expectedVersion") Long expectedVersion
);
```

读取、软删除和预览绑定 SQL 都同时包含 `id`、`customer_identity_id` 和 `status='ACTIVE'`。

- [ ] **Step 5: 实现方案服务**

保存过程固定为：

```java
Long customerId = identityResolver.requireCustomerIdentityId();
DiyEvaluation evaluation = pricingService.evaluate(request.toCommand());
Map<Long, CatalogSnapshot> snapshot = snapshotCatalog(request.sequence());
DiyScheme scheme = toEntity(customerId, request, evaluation, snapshot);

if (creating) {
    scheme.setVersion(0L);
    schemeMapper.insert(scheme);
} else {
    int updated = schemeMapper.updateOwnedWithVersion(
            scheme,
            customerId,
            request.version()
    );
    if (updated == 0) {
        ensureOwnedSchemeExists(scheme.getId(), customerId);
        throw new BusinessException(
                ErrorCode.DIY_SCHEME_VERSION_CONFLICT,
                "方案已在其他页面更新，请重新载入"
        );
    }
    scheme.setVersion(request.version() + 1);
}
```

`snapshotCatalog` 使用 `selectBatchIds` 读取当前名称、珠径、封面和状态；
缺失项使用 `SKU #<id>` 占位，只供恢复展示。
evaluation 可报价时保存四项金额和指纹；不可报价时金额与指纹置空，
quoteStatus 为 `INVALID`。

`CatalogSnapshot` 是 `DiySchemeService` 的私有嵌套 record：

```java
private record CatalogSnapshot(
        Long skuId,
        String skuCode,
        String name,
        BigDecimal diameterMm,
        String coverUrl,
        CatalogStatus status
) {
}
```

复制方案创建新主键、version 0、名称按 80 字符限制追加“ 副本”。
删除只把 status 改为 `DELETED`。

列表和详情返回前，用已保存的 sequence、腕围、松紧和绳材重新调用计价器。
若重新计算结果不可报价，响应 quoteStatus 为 `INVALID`；若仍可报价但指纹与保存值不同，
响应 quoteStatus 为 `STALE`；指纹相同才返回 `VALID`。新增集成测试先保存方案，
再把 `bead_sku.id=2001` 的 sale_price 增加 1 元，重新读取必须得到 `STALE`。

- [ ] **Step 6: 实现 REST 接口并运行测试**

接口映射严格使用：

```text
POST   /api/app/diy/schemes
GET    /api/app/diy/schemes
GET    /api/app/diy/schemes/{id}
PUT    /api/app/diy/schemes/{id}
POST   /api/app/diy/schemes/{id}/copies
DELETE /api/app/diy/schemes/{id}
```

创建返回 201，删除返回 200 且 data 为 `true`。

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn \
  -Dtest=AppDiySchemeControllerTest,AppDiyPricingControllerTest \
  test
```

Expected: PASS，顺序完整恢复、越权为 404、旧版本更新为 409。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/diybracelet/mall/diy \
  backend/src/main/java/com/diybracelet/mall/shared/error/ErrorCode.java \
  backend/src/test/java/com/diybracelet/mall/diy/api/AppDiySchemeControllerTest.java
git commit -m "feat: add DIY scheme lifecycle"
```

### Task 6: 实现方案预览图上传与绑定

**Files:**
- Modify: `backend/src/main/java/com/diybracelet/mall/diy/api/AppDiySchemeController.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/diy/application/DiySchemeService.java`
- Create: `backend/src/test/java/com/diybracelet/mall/diy/api/AppDiyPreviewControllerTest.java`

- [ ] **Step 1: 编写失败的预览图 Controller 测试**

使用 `@WebMvcTest(AppDiySchemeController.class)`，mock `DiySchemeService` 和
`FileStorageService`。传入顾客 token 和 PNG：

```java
MockMultipartFile file = new MockMultipartFile(
        "file",
        "scheme-preview.png",
        "image/png",
        "preview".getBytes(StandardCharsets.UTF_8)
);

mockMvc.perform(multipart("/api/app/diy/schemes/7001/preview")
                .file(file)
                .header("Authorization", "Bearer " + customerToken))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.previewUrl")
                .value("/api/public/files/2026/07/preview.png"));
```

- [ ] **Step 2: 运行测试并确认路径不存在**

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn -Dtest=AppDiyPreviewControllerTest test
```

Expected: FAIL，预览路径返回 404。

- [ ] **Step 3: 实现上传后绑定**

Controller：

```java
@PostMapping(
        value = "/schemes/{id}/preview",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
)
public ResponseEntity<ApiResponse<DiySchemeResponse>> uploadPreview(
        @PathVariable @Positive Long id,
        @RequestParam("file") MultipartFile file
) {
    UploadedFileResponse uploaded = fileStorageService.store(file);
    DiySchemeResponse scheme = schemeService.bindPreview(id, uploaded.id());
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(
                    scheme,
                    UUID.randomUUID().toString()
            ));
}
```

`bindPreview` 先按当前顾客读取方案，再确认 `asset_file.status='ACTIVE'`，
最后用所有权 SQL 更新 `preview_asset_id`。文件已保存但绑定失败时不删除文件，
避免跨存储实现补偿不一致；后续素材清理任务可回收未引用文件。

- [ ] **Step 4: 运行文件与预览测试**

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn \
  -Dtest=AppDiyPreviewControllerTest,FileControllerTest,LocalFileStorageTest \
  test
```

Expected: PASS，文件大小、类型和路径安全回归通过。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/diybracelet/mall/diy \
  backend/src/test/java/com/diybracelet/mall/diy/api/AppDiyPreviewControllerTest.java
git commit -m "feat: attach previews to DIY schemes"
```

### Task 7: 增加管理后台 DIY 配置工作区

**Files:**
- Create: `admin-web/src/api/diy.ts`
- Create: `admin-web/src/views/DiySettingsView.vue`
- Create: `admin-web/src/views/DiySettingsView.spec.ts`
- Modify: `admin-web/src/App.vue`
- Modify: `admin-web/src/style.css`

- [ ] **Step 1: 编写失败的配置页面测试**

```ts
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DiySettingsView from './DiySettingsView.vue'
import {
  getAdminDiyConfiguration,
  saveCraftFeeRules,
  saveDiyRule,
} from '../api/diy'

vi.mock('../api/diy', () => ({
  getAdminDiyConfiguration: vi.fn(),
  saveDiyRule: vi.fn(),
  saveCraftFeeRules: vi.fn(),
  createCordOption: vi.fn(),
  updateCordOption: vi.fn(),
}))

describe('DiySettingsView', () => {
  beforeEach(() => {
    vi.mocked(getAdminDiyConfiguration).mockResolvedValue({
      rule: {
        minWristMm: 120,
        maxWristMm: 250,
        snugAllowanceMm: 5,
        standardAllowanceMm: 10,
        looseAllowanceMm: 15,
        toleranceMm: 4,
        maxBeadCount: 60,
        configVersion: 1,
      },
      craftFeeRules: [
        {
          id: 5001,
          minBeadCount: 1,
          maxBeadCount: 60,
          fee: 10,
          ruleVersion: 1,
        },
      ],
      cordOptions: [],
    })
  })

  it('loads and saves sizing rules', async () => {
    const wrapper = mount(DiySettingsView)
    await flushPromises()

    await wrapper.get('[data-test="max-bead-count"]').setValue('50')
    await wrapper.get('[data-test="save-diy-rule"]').trigger('click')

    expect(saveDiyRule).toHaveBeenCalledWith(
      expect.objectContaining({ maxBeadCount: 50 }),
    )
  })

  it('blocks overlapping craft fee ranges', async () => {
    const wrapper = mount(DiySettingsView)
    await flushPromises()

    await wrapper.get('[data-test="add-craft-rule"]').trigger('click')
    await wrapper.get('[data-test="save-craft-rules"]').trigger('click')

    expect(wrapper.text()).toContain('工艺费区间不能重叠或留空档')
    expect(saveCraftFeeRules).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: 运行测试并确认组件不存在**

Run:

```bash
cd admin-web
pnpm test -- DiySettingsView.spec.ts
```

Expected: FAIL，无法解析 `DiySettingsView.vue` 或 `../api/diy`。

- [ ] **Step 3: 创建 API 类型和函数**

`admin-web/src/api/diy.ts` 导出：

```ts
export type OptionStatus = 'ENABLED' | 'DISABLED'

export interface DiyRule {
  minWristMm: number
  maxWristMm: number
  snugAllowanceMm: number
  standardAllowanceMm: number
  looseAllowanceMm: number
  toleranceMm: number
  maxBeadCount: number
  configVersion: number
}

export interface CraftFeeRule {
  id: number
  minBeadCount: number
  maxBeadCount: number
  fee: number
  ruleVersion: number
}

export interface CordOption {
  id: number
  code: string
  name: string
  colorHex: string
  extraFee: number
  status: OptionStatus
  sortOrder: number
  optionVersion: number
}

export interface DiyConfiguration {
  rule: DiyRule
  craftFeeRules: CraftFeeRule[]
  cordOptions: CordOption[]
}

export const getAdminDiyConfiguration = () =>
  request<DiyConfiguration>('/admin/diy/config')

export const saveDiyRule = (payload: Omit<DiyRule, 'configVersion'>) =>
  request<DiyConfiguration>('/admin/diy/config', {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
```

同文件实现 `saveCraftFeeRules`、`createCordOption`、`updateCordOption`，
路径与 Task 3 的管理接口完全一致。

- [ ] **Step 4: 实现配置页面**

页面使用三个 `<section class="settings-card">`：

1. 尺寸规则：7 个受控数字输入；
2. 工艺费：可增删行，保存前按 min 排序并验证从 1 连续覆盖 maxBeadCount；
3. 绳材：编码、名称、颜色、附加费、排序和状态。

金额输入转换必须使用：

```ts
function nonNegativeNumber(value: string): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error('请输入不小于 0 的数字')
  }
  return parsed
}
```

保存成功重新加载服务端数据；`ApiError` 显示后端 message 和 traceId。

- [ ] **Step 5: 在 App.vue 增加模块切换**

```ts
type AdminView = 'catalog' | 'diy'
const activeView = ref<AdminView>('catalog')
```

侧栏把商品目录和 DIY 规则改为 button，模板主区：

```vue
<main class="admin-content">
  <CatalogView v-if="activeView === 'catalog'" />
  <DiySettingsView v-else />
</main>
```

不要引入 Vue Router；当前只有两个顶层工作区，显式状态足够。

- [ ] **Step 6: 运行管理端测试和构建**

Run:

```bash
cd admin-web
pnpm test
pnpm build
```

Expected: 全部 Vitest 通过，`vue-tsc --noEmit` 与 Vite 构建返回 0。

- [ ] **Step 7: 提交**

```bash
git add admin-web/src/api/diy.ts \
  admin-web/src/views/DiySettingsView.vue \
  admin-web/src/views/DiySettingsView.spec.ts \
  admin-web/src/App.vue \
  admin-web/src/style.css
git commit -m "feat: add DIY settings administration"
```

### Task 8: 实现小程序纯编辑状态、历史、布局和预估

**Files:**
- Create: `miniprogram/miniprogram/domain/diy-types.ts`
- Create: `miniprogram/miniprogram/domain/editor-state.ts`
- Create: `miniprogram/miniprogram/domain/editor-history.ts`
- Create: `miniprogram/miniprogram/domain/bracelet-layout.ts`
- Create: `miniprogram/miniprogram/domain/price-estimate.ts`
- Create: `miniprogram/src/diy-editor-state.spec.ts`
- Create: `miniprogram/src/diy-layout.spec.ts`
- Create: `miniprogram/src/diy-price-estimate.spec.ts`
- Modify: `miniprogram/tsconfig.json`

- [ ] **Step 1: 编写失败的纯逻辑测试**

```ts
import {
  addBead,
  adjustSkuQuantity,
  moveBead,
  replaceBead,
} from '../miniprogram/domain/editor-state'
import { createHistory } from '../miniprogram/domain/editor-history'

describe('DIY editor state', () => {
  it('preserves exact order without per-bead business ids', () => {
    let sequence = addBead([], 101, null)
    sequence = addBead(sequence, 202, 0)
    sequence = addBead(sequence, 101, 1)
    sequence = replaceBead(sequence, 1, 303)
    sequence = moveBead(sequence, 2, 0)

    expect(sequence).toEqual([101, 101, 303])
  })

  it('adjusts the last occurrence of one sku', () => {
    expect(adjustSkuQuantity([101, 202, 101], 101, -1))
      .toEqual([101, 202])
    expect(adjustSkuQuantity([101, 202], 101, 1))
      .toEqual([101, 101, 202])
  })

  it('keeps at most fifty undo snapshots', () => {
    const history = createHistory<number[]>([], 50)
    for (let index = 1; index <= 55; index += 1) {
      history.push([index])
    }
    for (let index = 0; index < 50; index += 1) history.undo()

    expect(history.current()).toEqual([5])
    expect(history.canUndo()).toBe(false)
  })
})
```

布局测试断言 4 个同直径珠子角度相差约 `π/2`；
预估测试断言 `[2001, 2001, 2002]` 统计为 2 和 1，
价格 `1280 × 2 + 1680 = 4240` 分。

- [ ] **Step 2: 运行测试并确认模块不存在**

Run:

```bash
cd miniprogram
pnpm test -- diy-editor-state.spec.ts diy-layout.spec.ts diy-price-estimate.spec.ts
```

Expected: FAIL，Jest 无法解析 `miniprogram/domain` 文件。

- [ ] **Step 3: 实现不可变编辑命令**

```ts
export type BeadSequence = number[]

export function addBead(
  sequence: BeadSequence,
  skuId: number,
  selectedIndex: number | null,
): BeadSequence {
  const insertAt = selectedIndex === null
    ? sequence.length
    : Math.min(selectedIndex + 1, sequence.length)
  return [
    ...sequence.slice(0, insertAt),
    skuId,
    ...sequence.slice(insertAt),
  ]
}

export function removeBead(
  sequence: BeadSequence,
  index: number,
): BeadSequence {
  if (index < 0 || index >= sequence.length) return sequence
  return sequence.filter((_, current) => current !== index)
}

export function replaceBead(
  sequence: BeadSequence,
  index: number,
  skuId: number,
): BeadSequence {
  if (index < 0 || index >= sequence.length) return sequence
  return sequence.map((value, current) => current === index ? skuId : value)
}

export function moveBead(
  sequence: BeadSequence,
  from: number,
  to: number,
): BeadSequence {
  if (
    from < 0 ||
    from >= sequence.length ||
    to < 0 ||
    to >= sequence.length ||
    from === to
  ) return sequence
  const next = sequence.slice()
  const [value] = next.splice(from, 1)
  next.splice(to, 0, value)
  return next
}
```

`adjustSkuQuantity` 增加时插入到第一个连续同 SKU 区段末尾；
不存在该 SKU 时追加。减少时删除最后一个同 SKU。

- [ ] **Step 4: 实现历史、布局和整数分预估**

`createHistory` 内部保存深拷贝数组，`push` 时清空 redo，
past 超过 limit 时删除最早状态。

布局使用珠径占总直径的比例分配圆周角：

```ts
const totalDiameter = sequence.reduce(
  (sum, skuId) => sum + catalog[skuId].diameterMm,
  0,
)
let consumed = 0
return sequence.map((skuId, index) => {
  const bead = catalog[skuId]
  const centerLength = consumed + bead.diameterMm / 2
  const angle = -Math.PI / 2 + centerLength / totalDiameter * Math.PI * 2
  consumed += bead.diameterMm
  return {
    index,
    skuId,
    x: centerX + Math.cos(angle) * ringRadius,
    y: centerY + Math.sin(angle) * ringRadius,
    radius: bead.diameterMm * scale / 2,
    angle,
  }
})
```

空数组直接返回 `[]`。`scale` 受画布短边和最大珠径约束，确保珠子不超出画布。

预估只接受 `salePriceCents`：

```ts
export function estimatePriceCents(
  sequence: number[],
  catalog: Record<number, { salePriceCents: number }>,
  craftFeeCents: number,
  cordFeeCents: number,
): number {
  const beadTotal = sequence.reduce(
    (sum, skuId) => sum + (catalog[skuId]?.salePriceCents || 0),
    0,
  )
  return beadTotal + craftFeeCents + cordFeeCents
}
```

- [ ] **Step 5: 让 Jest 编译运行时纯模块并执行测试**

`miniprogram/tsconfig.json` 增加：

```json
{
  "include": [
    "src/**/*.ts",
    "miniprogram/domain/**/*.ts"
  ]
}
```

Run:

```bash
cd miniprogram
pnpm test
pnpm typecheck
```

Expected: 新增纯逻辑测试与现有测试全部通过，运行时类型检查返回 0。

- [ ] **Step 6: 提交**

```bash
git add miniprogram/miniprogram/domain \
  miniprogram/src/diy-editor-state.spec.ts \
  miniprogram/src/diy-layout.spec.ts \
  miniprogram/src/diy-price-estimate.spec.ts \
  miniprogram/tsconfig.json
git commit -m "feat: add testable DIY editor domain"
```

### Task 9: 接入小程序访客会话和 DIY API

**Files:**
- Create: `miniprogram/miniprogram/services/session.ts`
- Create: `miniprogram/miniprogram/services/diy.ts`
- Modify: `miniprogram/miniprogram/services/request.ts`
- Modify: `miniprogram/miniprogram/app.ts`
- Modify: `miniprogram/types/wechat-miniprogram.d.ts`
- Create: `miniprogram/src/diy-session.spec.ts`

- [ ] **Step 1: 编写失败的会话测试**

把安装 ID 和令牌存储抽象为可替换存储器：

```ts
import {
  createGuestSessionManager,
  type SessionTransport,
  type StringStorage,
} from '../miniprogram/services/session'

describe('guest session manager', () => {
  it('reuses installation id and coalesces concurrent refreshes', async () => {
    const values = new Map<string, string>()
    const storage: StringStorage = {
      get: (key) => values.get(key) || null,
      set: (key, value) => values.set(key, value),
    }
    const transport: SessionTransport = {
      exchange: jest.fn().mockResolvedValue({
        accessToken: 'customer-token',
        expiresInSeconds: 2592000,
        customerIdentityId: 91,
      }),
    }
    const manager = createGuestSessionManager(
      storage,
      transport,
      () => '3f6f7816-0f1a-4aa0-870f-f34f9195a2ec',
    )

    await manager.ensure()
    await Promise.all([
      manager.refresh(),
      manager.refresh(),
    ])

    expect(transport.exchange).toHaveBeenCalledTimes(2)
  })
})
```

- [ ] **Step 2: 运行测试并确认 session 模块不存在**

Run:

```bash
cd miniprogram
pnpm test -- diy-session.spec.ts
```

Expected: FAIL，无法解析 `services/session`。

- [ ] **Step 3: 实现会话管理器**

固定存储键：

```ts
const INSTALLATION_ID_KEY = 'diy_installation_id'
const CUSTOMER_TOKEN_KEY = 'diy_customer_access_token'
```

`ensure()` 有 token 时直接返回；无 token 时生成 UUID 并交换。
UUID 使用 `wx.getRandomValues` 不可用时，以时间、随机数和模板生成符合 UUID v4 格式的值。
`refresh()` 使用共享的 in-flight Promise 合并并发刷新，请求完成后清除该 Promise。

运行时 transport 调用：

```ts
wx.request<ApiEnvelope<GuestSession>>({
  url: `${apiBaseUrl()}/app/session/guest`,
  method: 'POST',
  data: { installationId },
  timeout: 10000,
  success: handleEnvelope,
  fail: handleNetworkError,
})
```

- [ ] **Step 4: 改造统一请求层**

`request` 在 `/public/` 之外的顾客请求附加 token。
收到 401 时检查当前调用栈的 `retried` 布尔值：为 false 时调用一次 `refresh()`，
然后以 `retried=true` 重发；重发仍为 401 时直接抛出 `ApiError`，不得继续递归。
`RequestOptions.data` 改为 `unknown`，支持 sequence 数组。

新增 multipart 上传：

```ts
export function upload<T>(
  path: string,
  filePath: string,
  name = 'file',
): Promise<T> {
  return ensureGuestToken().then((token) => new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${apiBaseUrl()}${path}`,
      filePath,
      name,
      header: { Authorization: `Bearer ${token}` },
      success(response) {
        handleUploadEnvelope<T>(response, resolve, reject)
      },
      fail(error) {
        reject(new ApiError(error.errMsg, 'NETWORK_ERROR'))
      },
    })
  }))
}
```

- [ ] **Step 5: 实现 DIY API 类型**

`services/diy.ts` 导出：

- `getDiyConfiguration()`
- `quoteDiy(payload)`
- `listSchemes()`
- `getScheme(id)`
- `createScheme(payload)`
- `updateScheme(id, payload)`
- `copyScheme(id)`
- `deleteScheme(id)`
- `uploadSchemePreview(id, filePath)`

类型字段与后端 DTO 同名；后端 decimal 在小程序端解析为 number，
同时在进入预估器前用 `Math.round(value * 100)` 转整数分。

- [ ] **Step 6: 扩充微信类型并启动时建立会话**

类型文件新增 `getStorageSync/setStorageSync/uploadFile/createSelectorQuery/canvasToTempFilePath`
实际使用到的签名。

`app.ts`：

```ts
import { ensureGuestToken } from './services/session'

App({
  globalData: {
    apiBaseUrl: 'http://localhost:8080/api',
  },
  onLaunch() {
    void ensureGuestToken()
  },
})
```

- [ ] **Step 7: 运行会话、类型和现有目录测试**

Run:

```bash
cd miniprogram
pnpm test
pnpm typecheck
```

Expected: 全部通过，公共目录请求仍可匿名执行，DIY 请求附带顾客令牌。

- [ ] **Step 8: 提交**

```bash
git add miniprogram/miniprogram/services \
  miniprogram/miniprogram/app.ts \
  miniprogram/types/wechat-miniprogram.d.ts \
  miniprogram/src/diy-session.spec.ts
git commit -m "feat: connect mini program guest DIY APIs"
```

### Task 10: 构建 Canvas 工作台和方案列表

**Files:**
- Create: `miniprogram/miniprogram/pages/diy/editor.json`
- Create: `miniprogram/miniprogram/pages/diy/editor.ts`
- Create: `miniprogram/miniprogram/pages/diy/editor.wxml`
- Create: `miniprogram/miniprogram/pages/diy/editor.wxss`
- Create: `miniprogram/miniprogram/pages/diy/schemes.json`
- Create: `miniprogram/miniprogram/pages/diy/schemes.ts`
- Create: `miniprogram/miniprogram/pages/diy/schemes.wxml`
- Create: `miniprogram/miniprogram/pages/diy/schemes.wxss`
- Create: `miniprogram/miniprogram/utils/diy-view-model.ts`
- Create: `miniprogram/src/diy-view-model.spec.ts`
- Modify: `miniprogram/miniprogram/pages/catalog/detail.ts`
- Modify: `miniprogram/miniprogram/pages/catalog/detail.wxml`
- Modify: `miniprogram/miniprogram/pages/home/index.wxml`
- Modify: `miniprogram/miniprogram/app.json`

- [ ] **Step 1: 编写失败的编辑器视图模型测试**

```ts
import {
  buildEditorSummary,
  restorePackageSequence,
} from '../miniprogram/utils/diy-view-model'

describe('DIY view model', () => {
  it('expands package quantities in stable order', () => {
    expect(restorePackageSequence([
      { beadSkuId: 2001, quantity: 2, sortOrder: 10 },
      { beadSkuId: 2002, quantity: 1, sortOrder: 20 },
    ])).toEqual([2001, 2001, 2002])
  })

  it('labels invalid draft without hiding its arrangement', () => {
    expect(buildEditorSummary({
      beadCount: 21,
      currentLengthMm: 168,
      targetLengthMm: 170,
      sizeStatus: 'FIT',
      estimatedPriceCents: 31480,
      authoritative: false,
    })).toEqual({
      countText: '21 颗',
      lengthText: '当前 168 mm / 目标 170 mm',
      sizeText: '尺寸合适',
      priceText: '预估 ¥314.80',
      priceBadge: '预估',
    })
  })
})
```

- [ ] **Step 2: 运行测试并确认视图模型不存在**

Run:

```bash
cd miniprogram
pnpm test -- diy-view-model.spec.ts
```

Expected: FAIL，无法解析 `diy-view-model`。

- [ ] **Step 3: 实现 Canvas 工作台页面状态**

页面 data 至少包含：

```ts
{
  loading: true,
  saving: false,
  schemeId: 0,
  schemeVersion: 0,
  name: '我的手串',
  wristCircumferenceMm: 160,
  fitType: 'STANDARD',
  cordOptionId: 6001,
  sequence: [],
  selectedIndex: -1,
  catalog: [],
  cordOptions: [],
  countText: '0 颗',
  lengthText: '尚未选珠',
  sizeText: '请选择珠子',
  priceText: '预估 ¥0.00',
  quoteIssues: [],
  canUndo: false,
  canRedo: false,
  errorMessage: '',
}
```

`onLoad` 支持 `packageId`、`schemeId` 和 `skuId`：

- packageId：加载套餐并按 sortOrder 展开；
- schemeId：加载后端方案原顺序；
- skuId：从单颗详情进入时创建 `[skuId]`；
- 无参数：如果本地存在未保存缓存，调用 `wx.showModal` 询问继续编辑或新建设计；
  无缓存或用户选择新建时使用空白设计。

每次编辑后：

1. 更新 history；
2. 清除权威报价标记；
3. 写入本地恢复缓存；
4. 重新计算摘要；
5. 调用 `drawBracelet()`。

保存成功并取得后端响应后清除本地编辑缓存。预览上传失败时不恢复整份编辑缓存，
而是单独保存 `{schemeId, previewPending: true}` 供方案列表重试。

- [ ] **Step 4: 实现 Canvas 初始化、绘制和命中**

WXML 使用：

```xml
<canvas
  id="bracelet-canvas"
  type="2d"
  class="bracelet-canvas"
  bindtouchstart="onCanvasTouchStart"
  bindtouchmove="onCanvasTouchMove"
  bindtouchend="onCanvasTouchEnd"
/>
```

Canvas 初始化后按设备像素比设置真实宽高。
绘制顺序：

1. 清空；
2. 绘制绳环；
3. 按 `computeBraceletLayout` 坐标绘制珠子封面；
4. 图片不可用时使用 SKU 颜色绘制圆；
5. 选中珠子绘制 2 px 高亮环；
6. 中心绘制珠数和尺寸状态。

命中使用点到圆心距离；拖动只记录目标 index，`touchend` 才调用 `moveBead`。

- [ ] **Step 5: 实现已确认的 A 布局**

WXML 固定结构：

```xml
<view class="diy-page">
  <view class="configuration-strip">
    <button bindtap="editWrist">腕围 {{wristCircumferenceMm / 10}} cm</button>
    <picker range="{{fitTypeLabels}}" value="{{fitTypeIndex}}"
      bindchange="changeFitType">{{fitTypeLabels[fitTypeIndex]}}</picker>
    <picker range="{{cordOptionNames}}" value="{{cordOptionIndex}}"
      bindchange="changeCord">{{cordOptionNames[cordOptionIndex]}}</picker>
  </view>

  <view class="canvas-panel">
    <canvas id="bracelet-canvas" type="2d" class="bracelet-canvas" />
    <view class="history-actions">
      <button disabled="{{!canUndo}}" bindtap="undo">↶</button>
      <button disabled="{{!canRedo}}" bindtap="redo">↷</button>
    </view>
    <view class="editor-summary">
      <text>{{countText}}</text>
      <text>{{lengthText}}</text>
      <text>{{sizeText}}</text>
    </view>
  </view>

  <view class="price-strip">{{priceText}}</view>
  <scroll-view scroll-x class="bead-tray">
    <button wx:for="{{catalog}}" wx:key="id" data-id="{{item.id}}"
      bindtap="addCatalogBead">{{item.name}}</button>
  </scroll-view>
  <view class="editor-actions">
    <button bindtap="saveDraft">保存草稿</button>
    <button bindtap="confirmQuote">确认报价</button>
  </view>
</view>
```

模板在 bead tray 前增加分类和搜索控件：

```xml
<scroll-view scroll-x class="category-tabs">
  <button data-id="0" bindtap="chooseCategory">全部</button>
  <button wx:for="{{categories}}" wx:key="id"
    data-id="{{item.id}}" bindtap="chooseCategory">{{item.name}}</button>
</scroll-view>
<view class="bead-search">
  <input value="{{keyword}}" bindinput="onKeywordInput" placeholder="搜索珠子" />
  <button bindtap="searchCatalog">搜索</button>
</view>
```

选中项操作区固定为：

```xml
<view wx:if="{{selectedIndex >= 0}}" class="selected-actions">
  <text>第 {{selectedIndex + 1}} 颗</text>
  <button bindtap="decreaseSelectedSku">−</button>
  <button bindtap="increaseSelectedSku">＋</button>
  <button bindtap="replaceSelected">替换</button>
  <button bindtap="removeSelected">删除</button>
</view>
<view wx:if="{{quoteIssues.length}}" class="quote-issues">
  <text wx:for="{{quoteIssues}}" wx:key="code">{{item.message}}</text>
</view>
```

- [ ] **Step 6: 实现保存、报价和预览**

`saveDraft` 调用新建或更新接口；成功后保存返回的 id/version。
随后调用 `wx.canvasToTempFilePath` 导出 PNG 并上传。
预览失败只设置“预览待重试”，不把保存结果改为失败。

`confirmQuote` 显示后端 issues；`quotable=true` 时替换价格文字为
`权威报价 ¥<total>`，任何编辑命令都恢复为“预估”。

- [ ] **Step 7: 实现方案列表**

方案列表 `onShow` 每次重新加载，卡片显示预览、名称、珠数、金额、
`VALID/STALE/INVALID/NOT_QUOTED` 中文状态和更新时间。
恢复进入 `/pages/diy/editor?schemeId=<id>`；
复制后刷新；删除先调用 `wx.showModal`，确认后软删除。

- [ ] **Step 8: 接通商品入口和页面注册**

`catalog/detail.ts`：

```ts
startDesign() {
  const parameter = this.data.type === 'package'
    ? `packageId=${this.data.id}`
    : `skuId=${this.data.id}`
  wx.navigateTo({ url: `/pages/diy/editor?${parameter}` })
}
```

`app.json` 增加：

```json
"pages/diy/editor",
"pages/diy/schemes"
```

首页增加“开始 DIY”和“我的方案”入口。

- [ ] **Step 9: 运行小程序测试和类型检查**

Run:

```bash
cd miniprogram
pnpm test
pnpm typecheck
```

Expected: 所有 Jest 测试通过，运行时 TypeScript 无错误。

- [ ] **Step 10: 提交**

```bash
git add miniprogram/miniprogram/pages/diy \
  miniprogram/miniprogram/utils/diy-view-model.ts \
  miniprogram/src/diy-view-model.spec.ts \
  miniprogram/miniprogram/pages/catalog/detail.ts \
  miniprogram/miniprogram/pages/catalog/detail.wxml \
  miniprogram/miniprogram/pages/home/index.wxml \
  miniprogram/miniprogram/app.json
git commit -m "feat: add mini program DIY workspace"
```

### Task 11: 完成真实闭环、文档和全仓验证

**Files:**
- Create: `scripts/diy-smoke.sh`
- Modify: `docs/requirements.md`
- Modify: `docs/api.md`
- Modify: `docs/data-model.md`
- Modify: `docs/development.md`
- Modify: `docs/deployment.md`
- Modify: `docs/testing.md`
- Modify: `docs/architecture.md`
- Modify: `docs/overview.md`
- Modify: `docs/progress.md`
- Modify: `README.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: 创建真实 HTTP 冒烟脚本**

脚本必须：

1. 生成随机安装 UUID；
2. `POST /api/app/session/guest`；
3. `GET /api/app/diy/config`；
4. 用 21 颗演示珠调用权威报价；
5. 新建方案；
6. 读取方案并校验顺序；
7. 复制方案；
8. 删除副本。

Node 断言核心：

```js
if (quote.code !== "OK" || quote.data?.quotable !== true) {
  throw new Error("DIY quote is not usable")
}
if (quote.data.totalAmount !== 314.8) {
  throw new Error(`unexpected total: ${quote.data.totalAmount}`)
}
if (
  scheme.code !== "OK" ||
  scheme.data?.sequence?.join(",") !== sequence.join(",")
) {
  throw new Error("saved scheme did not preserve SKU order")
}
```

脚本使用 `node -e` 构造和解析 JSON，不增加 `jq` 依赖。

- [ ] **Step 2: 启动真实后端并执行冒烟**

Run:

```bash
docker compose up -d mysql
source ./scripts/project-env.sh
cd backend
mvn spring-boot:run
```

另一个终端：

```bash
./scripts/catalog-smoke.sh
./scripts/diy-smoke.sh
```

Expected: 两个脚本均返回 0，输出商品目录和 DIY 闭环通过。

- [ ] **Step 3: 浏览器验证管理后台**

启动：

```bash
cd admin-web
pnpm dev --host 127.0.0.1
```

验证清单：

- 管理员登录；
- 进入 DIY 规则工作区；
- 修改最大珠数后保存并重新加载；
- 新增两个连续工艺费区间并保存；
- 创建或修改绳材；
- 输入重叠区间时页面阻止提交；
- 刷新浏览器后配置仍来自后端。

- [ ] **Step 4: 微信开发者工具验证小程序**

验证清单：

- 首次启动自动建立访客身份；
- 从套餐进入后顺序正确展开；
- 增删、替换、拖动、撤销和重做；
- 尺寸、预估和权威报价显示正确；
- 保存后退出，再进入“我的方案”恢复原顺序；
- 复制和删除方案；
- 后台下架一个 SKU 后旧方案标红并禁止报价；
- 断网编辑后重新进入可以恢复本地状态；
- 预览上传失败时方案仍存在。

- [ ] **Step 5: 更新权威文档**

文档必须写入实际接口、表、配置项、测试数量和限制。
`docs/progress.md` 的 DIY 与方案模块只在对应验收通过后调整完成度；
整体进度按 11 个模块重新计算，不使用工时估算。

明确保留以下限制：

- 未接微信授权；
- 未接真实对象存储；
- 未实现推荐、库存、订单、模拟支付和履约；
- 小程序访客方案只能在同一安装身份下访问。

- [ ] **Step 6: 执行全仓验证**

Run:

```bash
./scripts/verify.sh
git diff --check
git status --short
```

Expected:

- 后端所有 JUnit/Testcontainers 测试通过；
- 管理后台 Vitest、Vue 类型检查和生产构建通过；
- 小程序 Jest 和 TypeScript 类型检查通过；
- `git diff --check` 无输出；
- `git status --short` 只显示本任务预期文档与脚本。

- [ ] **Step 7: 提交**

```bash
git add scripts/diy-smoke.sh \
  docs README.md CHANGELOG.md
git commit -m "docs: complete DIY vertical slice"
```

## 3. 最终验收门槛

- [ ] 访客 token 与管理员 token 不能互换。
- [ ] 两个访客之间不能访问对方方案。
- [ ] SKU 顺序数组保存和恢复完全一致。
- [ ] 前端临时序号不进入数据库。
- [ ] 数量由顺序数组分组派生，不保存第二份可冲突数据。
- [ ] 尺寸、最大珠数、工艺费和绳材均可在后台调整。
- [ ] 非法尺寸允许保存草稿但不能权威报价。
- [ ] 权威报价只读取服务端 SKU 和配置。
- [ ] 方案更新使用乐观锁，旧版本返回 409。
- [ ] 预览上传失败不会丢失方案。
- [ ] `./scripts/verify.sh`、真实 HTTP 冒烟和三端人工烟测通过。
- [ ] 权威文档与 `docs/progress.md` 已更新为实际状态。
