# 商品目录与本地素材纵切 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可持久化管理和浏览的分类、珠子 SKU、成品套餐与本地素材能力，使管理后台维护的真实商品能够直接在微信小程序展示。

**Architecture:** 在现有 Spring Boot 模块化单体中新增 `catalog`、`file` 和最小 `auth` 模块。Flyway 管理 MySQL 结构，MyBatis-Plus 负责持久化，本地文件适配器实现稳定存储端口；公开查询 API 与受保护的后台写 API 共用应用服务。Vue 管理后台和原生小程序只依赖 REST 契约。

**Tech Stack:** Spring Boot 3.2.12, Spring Security, MyBatis-Plus, Flyway, MySQL 8, Testcontainers, JJWT, Vue 3, Element Plus, Vue Router, Pinia, Axios, Vitest, native WeChat mini program, Jest

---

## 已确认范围

- 设计依据：`docs/superpowers/specs/2026-07-04-diy-bracelet-mall-design.md`
- 素材默认写入 `storage/catalog/`，业务代码只依赖 `FileStorage` 接口。
- 管理端使用本地演示账号换取 JWT；接口结构保留数据库 RBAC 替换能力。
- 公共 API 只返回 `ON_SALE` 数据，后台 API 可以维护全部状态。
- 本计划不实现 DIY 编辑、权威计价、库存预占或订单。
- 金额统一使用 `BigDecimal` 与 `decimal(12,2)`。

### Task 1: 接入数据库、Flyway 与 Testcontainers

**Files:**

- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/db/migration/V1__catalog_schema.sql`
- Create: `backend/src/main/resources/db/migration/V2__catalog_seed.sql`
- Create: `backend/src/test/java/com/diybracelet/mall/support/MySqlIntegrationTest.java`
- Create: `backend/src/test/java/com/diybracelet/mall/catalog/infrastructure/CatalogMigrationTest.java`

- [ ] **Step 1: 写迁移红灯测试**

```java
class CatalogMigrationTest extends MySqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateCatalogTablesAndSeedVisibleProducts() {
        Integer categories = jdbcTemplate.queryForObject(
            "select count(*) from category",
            Integer.class
        );
        Integer beads = jdbcTemplate.queryForObject(
            "select count(*) from bead_sku where status = 'ON_SALE'",
            Integer.class
        );

        assertThat(categories).isGreaterThanOrEqualTo(2);
        assertThat(beads).isGreaterThanOrEqualTo(4);
    }
}
```

- [ ] **Step 2: 运行测试确认因基类或表不存在而失败**

```bash
cd backend
mvn -Dtest=CatalogMigrationTest test
```

- [ ] **Step 3: 在 `pom.xml` 增加依赖**

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.7</version>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 4: 配置运行时数据源**

```yaml
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:diy_bracelet}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:diy_bracelet}
    password: ${MYSQL_PASSWORD:change-me}
  flyway:
    enabled: true
    locations: classpath:db/migration

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

- [ ] **Step 5: 实现 MySQL 测试基类**

```java
@SpringBootTest
@Testcontainers
public abstract class MySqlIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
        new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("diy_bracelet_test")
            .withUsername("test")
            .withPassword("test");
}
```

- [ ] **Step 6: 创建完整目录迁移**

`V1__catalog_schema.sql` 创建以下表和外键：

```sql
create table asset_file (
    id bigint primary key auto_increment,
    storage_key varchar(255) not null unique,
    original_name varchar(255) not null,
    content_type varchar(100) not null,
    size_bytes bigint not null,
    public_url varchar(500) not null,
    status varchar(32) not null default 'ACTIVE',
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table category (
    id bigint primary key auto_increment,
    name varchar(80) not null,
    slug varchar(80) not null unique,
    sort_order int not null default 0,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table bead_sku (
    id bigint primary key auto_increment,
    category_id bigint not null,
    sku_code varchar(64) not null unique,
    name varchar(120) not null,
    material varchar(80) not null,
    color varchar(80) not null,
    diameter_mm decimal(5,2) not null,
    sale_price decimal(12,2) not null,
    cost_price decimal(12,2) not null,
    stock_alert_threshold int not null default 0,
    cover_asset_id bigint null,
    tags_json json not null,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_bead_category foreign key (category_id) references category(id),
    constraint fk_bead_cover foreign key (cover_asset_id) references asset_file(id)
);

create table product_package (
    id bigint primary key auto_increment,
    package_code varchar(64) not null unique,
    name varchar(120) not null,
    description varchar(1000) not null,
    sale_price decimal(12,2) not null,
    cover_asset_id bigint null,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint fk_package_cover foreign key (cover_asset_id) references asset_file(id)
);

create table package_item (
    id bigint primary key auto_increment,
    package_id bigint not null,
    bead_sku_id bigint not null,
    quantity int not null,
    sort_order int not null,
    constraint fk_item_package foreign key (package_id) references product_package(id),
    constraint fk_item_bead foreign key (bead_sku_id) references bead_sku(id),
    unique key uk_package_sort (package_id, sort_order)
);
```

`V2__catalog_seed.sql` 使用固定的 1001 起始主键写入 2 个分类、4 个已上架珠子和 1 个套餐。

- [ ] **Step 7: 验证并提交**

```bash
cd backend
mvn -Dtest=CatalogMigrationTest test
mvn test
git add .
git commit -m "feat: add catalog database migrations"
```

### Task 2: 建立目录领域模型与 Mapper

**Files:**

- Create: `backend/src/main/java/com/diybracelet/mall/catalog/domain/CatalogStatus.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/domain/{Category,BeadSku,ProductPackage,PackageItem}.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/infrastructure/mapper/{Category,BeadSku,ProductPackage,PackageItem}Mapper.java`
- Create: `backend/src/test/java/com/diybracelet/mall/catalog/infrastructure/CatalogMapperTest.java`

- [ ] **Step 1: 写查询红灯测试**

```java
class CatalogMapperTest extends MySqlIntegrationTest {

    @Autowired
    private BeadSkuMapper beadSkuMapper;

    @Test
    void shouldFilterVisibleBeadsByCategoryAndKeyword() {
        var result = beadSkuMapper.selectPublicPage(1001L, "水晶", 0, 20);

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(
            bead -> bead.getStatus() == CatalogStatus.ON_SALE
        );
    }
}
```

- [ ] **Step 2: 运行测试确认 `BeadSkuMapper` 不存在**

```bash
cd backend
mvn -Dtest=CatalogMapperTest test
```

- [ ] **Step 3: 实现状态、实体和 Mapper**

```java
public enum CatalogStatus {
    DRAFT,
    ON_SALE,
    OFF_SALE
}
```

实体使用 `@TableName`、`@TableId(type = IdType.AUTO)`，字段类型与迁移一致，并提供无参构造器、显式 getter/setter 和解释状态/金额约束的 Javadoc。

```java
@Mapper
public interface BeadSkuMapper extends BaseMapper<BeadSku> {

    @Select("""
        <script>
        select * from bead_sku
        where status = 'ON_SALE'
        <if test='categoryId != null'>
          and category_id = #{categoryId}
        </if>
        <if test='keyword != null and keyword != ""'>
          and (name like concat('%', #{keyword}, '%')
               or material like concat('%', #{keyword}, '%'))
        </if>
        order by id desc
        limit #{limit} offset #{offset}
        </script>
        """)
    List<BeadSku> selectPublicPage(
        @Param("categoryId") Long categoryId,
        @Param("keyword") String keyword,
        @Param("offset") long offset,
        @Param("limit") long limit
    );
}
```

- [ ] **Step 4: 验证并提交**

```bash
cd backend
mvn -Dtest=CatalogMapperTest test
mvn test
git add src
git commit -m "feat: add catalog persistence model"
```

### Task 3: 实现可替换本地文件存储

**Files:**

- Create: `backend/src/main/java/com/diybracelet/mall/file/domain/{FileStorage,StoredFile}.java`
- Create: `backend/src/main/java/com/diybracelet/mall/file/infrastructure/LocalFileStorage.java`
- Create: `backend/src/main/java/com/diybracelet/mall/file/application/FileStorageService.java`
- Create: `backend/src/main/java/com/diybracelet/mall/file/api/{AdminFileController,PublicFileController}.java`
- Create: `backend/src/test/java/com/diybracelet/mall/file/infrastructure/LocalFileStorageTest.java`

- [ ] **Step 1: 写文件安全红灯测试**

```java
class LocalFileStorageTest {

    @TempDir
    Path root;

    @Test
    void shouldStoreInsideConfiguredRoot() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root, 5_242_880);
        var stored = storage.store(
            "bracelet.png",
            "image/png",
            "image-data".getBytes()
        );

        assertThat(Files.readAllBytes(root.resolve(stored.storageKey())))
            .isEqualTo("image-data".getBytes());
    }

    @Test
    void shouldRejectExecutableAndTraversalName() {
        LocalFileStorage storage = new LocalFileStorage(root, 5_242_880);

        assertThatThrownBy(() ->
            storage.store("../attack.sh", "text/x-shellscript", new byte[0])
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: 运行测试确认实现不存在**

```bash
cd backend
mvn -Dtest=LocalFileStorageTest test
```

- [ ] **Step 3: 定义端口**

```java
public interface FileStorage {

    StoredFile store(String originalName, String contentType, byte[] content);

    byte[] read(String storageKey);
}
```

```java
public record StoredFile(
    String storageKey,
    String originalName,
    String contentType,
    long sizeBytes,
    String publicUrl
) {
}
```

- [ ] **Step 4: 实现本地适配器和 API**

实现必须满足：

- 仅允许 PNG、JPEG、WebP，默认最大 5 MiB；
- 存储键使用 UUID，不拼接原始文件名；
- `Path.normalize()` 后目标必须仍位于配置根；
- 文件写入 `storage/catalog/YYYY/MM/`；
- `POST /api/admin/files` 上传，`GET /api/public/files/{key}` 读取；
- 不存在键返回 404 `COMMON_NOT_FOUND`。

- [ ] **Step 5: 验证并提交**

```bash
cd backend
mvn -Dtest=LocalFileStorageTest test
mvn test
git add src ../storage ../docs
git commit -m "feat: add local catalog asset storage"
```

### Task 4: 增加本地管理员 JWT 认证骨架

**Files:**

- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/diybracelet/mall/auth/api/AdminAuthController.java`
- Create: `backend/src/main/java/com/diybracelet/mall/auth/application/AdminAuthService.java`
- Create: `backend/src/main/java/com/diybracelet/mall/auth/infrastructure/{JwtService,JwtAuthenticationFilter,SecurityConfiguration}.java`
- Create: `backend/src/test/java/com/diybracelet/mall/auth/api/AdminAuthControllerTest.java`

- [ ] **Step 1: 写认证红灯测试**

```java
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldIssueTokenForDemoAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"admin","password":"Admin@123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void shouldRejectAnonymousAdminRequest() throws Exception {
        mockMvc.perform(get("/api/admin/catalog/beads"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: 运行测试确认登录端点不存在**

```bash
cd backend
mvn -Dtest=AdminAuthControllerTest test
```

- [ ] **Step 3: 增加 Spring Security 和 JJWT 0.12.6 依赖**

`jjwt-api` 为编译依赖，`jjwt-impl`、`jjwt-jackson` 为 runtime；演示账号、JWT secret 和 8 小时 TTL 全部从 `app.auth` 配置读取。

- [ ] **Step 4: 实现无状态安全链**

```java
http
    .csrf(AbstractHttpConfigurer::disable)
    .sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    )
    .authorizeHttpRequests(auth -> auth
        .requestMatchers(
            "/api/public/**",
            "/api/admin/auth/login",
            "/actuator/health"
        ).permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated()
    )
    .addFilterBefore(
        jwtAuthenticationFilter,
        UsernamePasswordAuthenticationFilter.class
    );
```

错误密码返回 HTTP 401 和 `COMMON_UNAUTHORIZED`；日志不得打印密码或 JWT。

- [ ] **Step 5: 验证并提交**

```bash
cd backend
mvn -Dtest=AdminAuthControllerTest test
mvn test
git add . ../.env.example ../docs
git commit -m "feat: add local admin JWT authentication"
```

### Task 5: 实现公开目录查询 API

**Files:**

- Create: `backend/src/main/java/com/diybracelet/mall/catalog/api/PublicCatalogController.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/api/dto/{CategorySummary,BeadSkuSummary,BeadSkuDetail,PackageSummary,PackageDetail,PageResponse}.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/application/CatalogQueryService.java`
- Create: `backend/src/test/java/com/diybracelet/mall/catalog/api/PublicCatalogControllerTest.java`

- [ ] **Step 1: 写公开 API 红灯测试**

```java
class PublicCatalogControllerTest extends MySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldListVisibleBeadsWithStablePageContract() throws Exception {
        mockMvc.perform(get("/api/public/catalog/beads")
                .queryParam("page", "1")
                .queryParam("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.items[0].skuCode").isNotEmpty())
            .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    void shouldReturnBusinessNotFoundForMissingBead() throws Exception {
        mockMvc.perform(get("/api/public/catalog/beads/999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
    }
}
```

- [ ] **Step 2: 运行测试确认端点不存在**

```bash
cd backend
mvn -Dtest=PublicCatalogControllerTest test
```

- [ ] **Step 3: 实现以下端点**

```text
GET /api/public/catalog/categories
GET /api/public/catalog/beads?categoryId=&keyword=&page=1&size=20
GET /api/public/catalog/beads/{id}
GET /api/public/catalog/packages?page=1&size=20
GET /api/public/catalog/packages/{id}
```

```java
public record PageResponse<T>(
    List<T> items,
    long total,
    int page,
    int size
) {
}
```

约束 `page >= 1`、`size` 为 1–100；列表和详情只查询 `ON_SALE`。

- [ ] **Step 4: 验证并提交**

```bash
cd backend
mvn -Dtest=PublicCatalogControllerTest test
mvn test
git add src ../docs/api.md
git commit -m "feat: add public catalog query API"
```

### Task 6: 实现后台商品维护 API

**Files:**

- Create: `backend/src/main/java/com/diybracelet/mall/catalog/api/AdminCatalogController.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/api/dto/{SaveCategoryRequest,SaveBeadSkuRequest,SavePackageRequest,SavePackageItemRequest,ChangeCatalogStatusRequest}.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/application/AdminCatalogService.java`
- Create: `backend/src/test/java/com/diybracelet/mall/catalog/api/AdminCatalogControllerTest.java`

- [ ] **Step 1: 写后台新增红灯测试**

```java
@Test
void shouldCreateBeadWithAdminToken() throws Exception {
    String token = loginAsAdmin(mockMvc);

    mockMvc.perform(post("/api/admin/catalog/beads")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "categoryId":1001,
                  "skuCode":"BEAD-TEST-001",
                  "name":"测试白水晶",
                  "material":"水晶",
                  "color":"透明",
                  "diameterMm":8.00,
                  "salePrice":12.80,
                  "costPrice":5.20,
                  "stockAlertThreshold":10,
                  "tags":["净化","日常"],
                  "status":"ON_SALE"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.skuCode").value("BEAD-TEST-001"));
}
```

- [ ] **Step 2: 运行测试确认后台端点不存在**

```bash
cd backend
mvn -Dtest=AdminCatalogControllerTest test
```

- [ ] **Step 3: 实现后台契约**

```text
GET/POST/PUT /api/admin/catalog/categories
PATCH        /api/admin/catalog/categories/{id}/status
GET/POST/PUT /api/admin/catalog/beads
PATCH        /api/admin/catalog/beads/{id}/status
GET/POST/PUT /api/admin/catalog/packages
PATCH        /api/admin/catalog/packages/{id}/status
```

请求使用 Bean Validation；`AdminCatalogService` 使用 `@Transactional`；套餐明细按请求顺序整体替换；重复 slug 或编码返回 409，引用不存在的分类、素材或珠子返回 404；仍被商品引用的分类不能删除，只能下架。

- [ ] **Step 4: 验证并提交**

```bash
cd backend
mvn -Dtest=AdminCatalogControllerTest test
mvn test
git add src ../docs
git commit -m "feat: add catalog administration API"
```

### Task 7: 交付 Vue 商品管理页面

**Files:**

- Modify: `admin-web/package.json`
- Modify: `admin-web/src/{main.ts,App.vue}`
- Create: `admin-web/src/api/{http,auth,catalog}.ts`
- Create: `admin-web/src/router/index.ts`
- Create: `admin-web/src/stores/auth.ts`
- Create: `admin-web/src/views/{LoginView,CatalogView}.vue`
- Create: `admin-web/src/views/CatalogView.spec.ts`

- [ ] **Step 1: 写页面红灯测试**

```ts
vi.mock('../api/catalog', () => ({
  listAdminBeads: vi.fn().mockResolvedValue({
    items: [{
      id: 1001,
      skuCode: 'BEAD-CRYSTAL-001',
      name: '白水晶 8mm',
      salePrice: 12.8,
      status: 'ON_SALE',
    }],
    total: 1,
    page: 1,
    size: 20,
  }),
}))

it('loads catalog rows through the API module', async () => {
  const wrapper = mount(CatalogView)
  await flushPromises()

  expect(wrapper.text()).toContain('白水晶 8mm')
  expect(wrapper.text()).toContain('BEAD-CRYSTAL-001')
  expect(wrapper.get('[data-test="create-bead"]').exists()).toBe(true)
})
```

- [ ] **Step 2: 增加并安装依赖**

```json
{
  "axios": "1.7.9",
  "element-plus": "2.9.1",
  "pinia": "2.3.0",
  "vue-router": "4.5.0"
}
```

```bash
cd admin-web
pnpm install
pnpm test
```

Expected: FAIL，`CatalogView.vue` 不存在。

- [ ] **Step 3: 实现 HTTP 与认证**

```ts
export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
  timeout: 10_000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_access_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
```

路由包含 `/login` 与 `/catalog`；未登录重定向登录，401 清除 token。

- [ ] **Step 4: 实现目录页面**

页面包含珠子表格、筛选、新建/编辑表单、封面上传、上下架、套餐页签和有序套餐明细；错误消息直接展示后端 `message`。

- [ ] **Step 5: 验证并提交**

```bash
cd admin-web
pnpm test
pnpm build
git add .
git commit -m "feat: add catalog administration screens"
```

### Task 8: 交付小程序商品浏览

**Files:**

- Modify: `miniprogram/miniprogram/app.json`
- Modify: `miniprogram/miniprogram/pages/home/index.wxml`
- Create: `miniprogram/miniprogram/services/{request,catalog}.ts`
- Create: `miniprogram/miniprogram/pages/catalog/index.*`
- Create: `miniprogram/miniprogram/pages/catalog/detail.*`
- Create: `miniprogram/src/{catalog-view-model.ts,catalog-view-model.spec.ts}`

- [ ] **Step 1: 写转换红灯测试**

```ts
it('formats price and missing image consistently', () => {
  expect(toBeadCard({
    id: 1001,
    skuCode: 'BEAD-CRYSTAL-001',
    name: '白水晶 8mm',
    material: '水晶',
    color: '透明',
    diameterMm: 8,
    salePrice: 12.8,
    coverUrl: null,
    tags: ['净化'],
  })).toEqual({
    id: 1001,
    title: '白水晶 8mm',
    specification: '水晶 · 透明 · 8mm',
    priceText: '¥12.80 / 颗',
    imageUrl: '/assets/images/bead-placeholder.png',
    tags: ['净化'],
  })
})
```

- [ ] **Step 2: 运行测试确认转换函数不存在**

```bash
cd miniprogram
pnpm test
```

- [ ] **Step 3: 实现纯转换函数**

```ts
export function toBeadCard(bead: PublicBead): BeadCard {
  return {
    id: bead.id,
    title: bead.name,
    specification:
      `${bead.material} · ${bead.color} · ${formatDiameter(bead.diameterMm)}mm`,
    priceText: `¥${Number(bead.salePrice).toFixed(2)} / 颗`,
    imageUrl: bead.coverUrl ?? '/assets/images/bead-placeholder.png',
    tags: bead.tags,
  }
}
```

- [ ] **Step 4: 实现请求、分类列表和详情**

支持分类切换、搜索、分页、空状态、失败重试、珠子详情和套餐组成；后端非 `OK` 响应转换为带 `code/message` 的错误。

- [ ] **Step 5: 验证并提交**

```bash
cd miniprogram
pnpm test
node -e "JSON.parse(require('fs').readFileSync('project.config.json','utf8')); JSON.parse(require('fs').readFileSync('miniprogram/app.json','utf8'))"
git add .
git commit -m "feat: add mini program catalog browsing"
```

### Task 9: 更新权威文档与真实冒烟

**Files:**

- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/{requirements,architecture,data-model,api,development,setup,testing}.md`
- Modify: `scripts/verify.sh`
- Create: `scripts/catalog-smoke.sh`

- [ ] **Step 1: 增加真实 API 冒烟脚本**

```bash
#!/usr/bin/env bash
set -euo pipefail

base_url="${BACKEND_BASE_URL:-http://localhost:8080}"
health="$(curl -fsS "${base_url}/actuator/health")"
catalog="$(curl -fsS "${base_url}/api/public/catalog/beads?page=1&size=20")"

node -e '
const health = JSON.parse(process.argv[1])
const catalog = JSON.parse(process.argv[2])
if (health.status !== "UP") throw new Error("backend health is not UP")
if (catalog.code !== "OK") throw new Error("catalog API did not return OK")
if (!Array.isArray(catalog.data.items) || catalog.data.items.length === 0) {
  throw new Error("catalog seed data is empty")
}
' "${health}" "${catalog}"
```

- [ ] **Step 2: 更新当前版本文档**

明确记录演示管理员限制、目录表字段与状态、API 示例、本地素材安全边界、管理操作、小程序验证步骤，以及尚未实现的 DIY/库存/订单范围。

- [ ] **Step 3: 启动后端并运行冒烟**

```bash
docker compose up -d
cd backend
mvn spring-boot:run
```

在另一终端：

```bash
./scripts/catalog-smoke.sh
```

Expected: 健康状态为 `UP`，目录至少返回 4 个种子珠子。

- [ ] **Step 4: 运行最终验证**

```bash
./scripts/check-prerequisites.sh
./scripts/verify.sh
docker compose ps
git diff --check
git status --short
```

- [ ] **Step 5: 提交**

```bash
git add README.md CHANGELOG.md docs scripts
git commit -m "docs: complete catalog vertical slice"
```

## 完成标准

- Flyway 可在空 MySQL 8 实例重复创建目录结构和演示数据；
- 本地素材阻止目录穿越、脚本和超限文件；
- 演示管理员可维护分类、珠子、套餐和素材；
- 未认证请求不能调用后台目录 API；
- 小程序只显示已上架商品和套餐；
- 后端单元/集成测试、管理后台测试与构建、小程序 Jest 全部通过；
- 公开目录真实 HTTP 冒烟通过；
- 权威文档与当前实现一致。
