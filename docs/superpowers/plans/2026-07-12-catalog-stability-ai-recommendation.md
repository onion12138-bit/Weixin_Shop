# Catalog Stability and AI Recommendation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore every mini-program customer path, import three real bead products with inventory, add one secure DeepSeek configuration, and generate one backend-validated AI bracelet recommendation that can enter the DIY editor.

**Architecture:** Execute four dependent vertical slices: fixed local networking, catalog/inventory import, server-side DeepSeek configuration, then customer recommendation. Spring Boot remains the authority for identity, SKU availability, inventory, sizing, and pricing; Vue configures the provider; the native WeChat mini program collects preferences and renders only validated results.

**Tech Stack:** Java 17, Spring Boot 3.2, MyBatis-Plus, Flyway, MySQL 8, Java `HttpClient`, AES-GCM, Vue 3, TypeScript, Vitest, native WeChat mini program, Jest, Bash, `sips`, curl.

## Global Constraints

- Local project services use backend port `8081`; Jenkins on `8080` must not be stopped or modified.
- Preserve the existing uncommitted `miniprogram/project.config.json` and the original untracked `Pic/` directory.
- Import only Labradorite, Garnet, and Fluorite at 8mm with stock 200.
- Default model is `deepseek-v4-flash` at `https://api.deepseek.com`.
- Maintain only one current AI configuration; never return or log the API key.
- Do not implement local recommendation fallback, multi-turn chat, real WeChat login, real payment, or object storage.
- Birth date and full consultation text are not persisted.
- All metaphysical copy includes “文化娱乐参考” and makes no deterministic promises.
- Follow TDD: every behavior change starts with a failing test and each task ends with a focused commit.

---

## File Structure

- `miniprogram/miniprogram/config/runtime.ts`: one mini-program API origin.
- `backend/.../catalog/api/dto/CreateBeadSkuRequest.java`: create-only inventory input.
- `backend/.../catalog/application/AdminCatalogService.java`: transactional bead + inventory initialization.
- `assets/demo-catalog/`: three deterministic derived product covers and one compressed AI entrance image.
- `scripts/import-demo-beads.sh`: idempotent upload/create importer using admin APIs.
- `backend/.../recommendation/`: AI configuration, encryption, DeepSeek gateway, candidate query, validation, and controllers.
- `admin-web/src/views/AiConfigurationView.vue`: single provider configuration UI.
- `miniprogram/miniprogram/pages/recommendation/`: form and validated result page.
- `miniprogram/miniprogram/domain/recommendation-profile.ts`: birth-date derivation and temporary-draft conversion.

### Task 1: Fix the local port contract across all clients

**Files:**
- Create: `miniprogram/miniprogram/config/runtime.ts`
- Create: `miniprogram/src/runtime-config.spec.ts`
- Modify: `.env.example`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `admin-web/src/api/http.ts`
- Modify: `miniprogram/miniprogram/app.ts`
- Modify: `scripts/start-local.sh`
- Modify: `scripts/start-local.test.sh`
- Modify: `scripts/catalog-smoke.sh`
- Modify: `scripts/diy-smoke.sh`
- Modify: `scripts/order-smoke.sh`

**Interfaces:**
- Produces: `LOCAL_API_BASE_URL = 'http://localhost:8081/api'` and a fixed `BACKEND_PORT=8081` local contract.
- Consumes: existing startup readiness checks and `App.globalData.apiBaseUrl`.

- [ ] **Step 1: Write failing port-contract tests**

Add `miniprogram/src/runtime-config.spec.ts`:

```ts
import { LOCAL_API_BASE_URL } from '../miniprogram/config/runtime'

describe('mini program runtime config', () => {
  it('uses the dedicated local backend instead of Jenkins', () => {
    expect(LOCAL_API_BASE_URL).toBe('http://localhost:8081/api')
  })
})
```

Change `scripts/start-local.test.sh` so its fake `lsof` marks 8081 occupied by an unrelated service and assert the launcher exits non-zero instead of scanning 8082. Add `rg -n "localhost:8080"` assertions limited to active runtime files.

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
cd miniprogram && pnpm test -- runtime-config.spec.ts
cd .. && bash scripts/start-local.test.sh
```

Expected: Jest fails because `config/runtime.ts` does not exist; the shell test fails because the launcher still scans fallback ports.

- [ ] **Step 3: Implement the fixed 8081 contract**

Create:

```ts
export const LOCAL_API_BASE_URL = 'http://localhost:8081/api'
```

Use it in `app.ts`:

```ts
import { LOCAL_API_BASE_URL } from './config/runtime'
import { ensureGuestToken } from './services/session'

App({
  globalData: { apiBaseUrl: LOCAL_API_BASE_URL },
  onLaunch() { void ensureGuestToken() },
})
```

Set backend defaults to 8081 in `.env.example`, `application.yml`, Vue `http.ts`, and the three smoke scripts. In `start-local.sh`, use `${BACKEND_PORT:-8081}` and fail when 8081 is occupied by anything other than a healthy project backend. Keep the existing no-kill behavior.

- [ ] **Step 4: Verify GREEN and real routing**

Run:

```bash
cd miniprogram && pnpm test -- runtime-config.spec.ts && pnpm exec tsc -p tsconfig.runtime.json
cd .. && bash scripts/start-local.test.sh
rg -n "localhost:8080" .env.example backend/src/main/resources admin-web/src miniprogram/miniprogram scripts
```

Expected: tests pass; `rg` reports only explicit Jenkins diagnostics in tests, not client defaults.

- [ ] **Step 5: Commit**

```bash
git add .env.example backend/src/main/resources/application.yml admin-web/src/api/http.ts miniprogram/miniprogram/app.ts miniprogram/miniprogram/config/runtime.ts miniprogram/src/runtime-config.spec.ts scripts/start-local.sh scripts/start-local.test.sh scripts/catalog-smoke.sh scripts/diy-smoke.sh scripts/order-smoke.sh
git commit -m "fix: unify local clients on backend port 8081"
```

### Task 2: Make guest-session recovery explicit and bounded

**Files:**
- Modify: `miniprogram/miniprogram/services/session.ts`
- Modify: `miniprogram/src/diy-session.spec.ts`
- Modify: `miniprogram/miniprogram/services/request.ts`

**Interfaces:**
- Produces: `GuestSessionManager.invalidate(): void`; one refresh attempt after customer 401.
- Consumes: `CUSTOMER_TOKEN_KEY`, `refreshGuestToken()`, and request retry flag.

- [ ] **Step 1: Write failing stale-token tests**

Extend `StringStorage` with `remove(key)` and test:

```ts
manager.invalidate()
expect(values.has('diy_customer_access_token')).toBe(false)
await manager.ensure()
expect(transport.exchange).toHaveBeenCalledTimes(2)
```

Keep the existing `retried` boolean in `request.ts` as the hard retry bound. Add a focused assertion around an exported `shouldRefreshCustomerSession(path, status, retried)` predicate: customer paths refresh only for `401 && !retried`; public paths and second 401 responses return false.

- [ ] **Step 2: Run test and verify RED**

Run: `cd miniprogram && pnpm test -- diy-session.spec.ts`

Expected: TypeScript/Jest fails because `remove` and `invalidate` do not exist.

- [ ] **Step 3: Implement invalidation**

Add to the interfaces and manager:

```ts
export interface StringStorage {
  get(key: string): string | null
  set(key: string, value: string): void
  remove(key: string): void
}

export interface GuestSessionManager {
  ensure(): Promise<string>
  refresh(): Promise<string>
  invalidate(): void
  currentToken(): string | null
}
```

The concrete storage uses `wx.removeStorageSync`. Before the single 401 refresh, call `guestSessionManager.invalidate()`. Preserve `retried=true` as the hard bound.

- [ ] **Step 4: Verify GREEN**

Run: `cd miniprogram && pnpm test -- diy-session.spec.ts && pnpm exec tsc -p tsconfig.runtime.json`

Expected: session tests and runtime type-check pass.

- [ ] **Step 5: Commit**

```bash
git add miniprogram/miniprogram/services/session.ts miniprogram/miniprogram/services/request.ts miniprogram/src/diy-session.spec.ts
git commit -m "fix: recover stale mini program guest sessions once"
```

### Task 3: Create beads and initial inventory atomically

**Files:**
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/api/dto/CreateBeadSkuRequest.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/catalog/api/AdminCatalogController.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/catalog/application/AdminCatalogService.java`
- Modify: `backend/src/test/java/com/diybracelet/mall/catalog/api/AdminCatalogControllerTest.java`
- Modify: `admin-web/src/api/catalog.ts`
- Modify: `admin-web/src/views/BeadCatalogPanel.vue`
- Modify: `admin-web/src/views/CatalogView.spec.ts`

**Interfaces:**
- Produces: flat POST payload with all `SaveBeadSkuRequest` fields plus `initialStock: number`.
- Consumes: `InventoryBalanceMapper`, `InventoryLedgerMapper`, existing SKU uniqueness and asset validation.

- [ ] **Step 1: Write failing transactional API test**

Post a bead with `initialStock: 200`, then query MySQL and assert:

```java
assertThat(balance.getAvailableQty()).isEqualTo(200);
assertThat(balance.getReservedQty()).isZero();
assertThat(balance.getSoldQty()).isZero();
assertThat(ledger.getChangeType()).isEqualTo("INIT");
assertThat(ledger.getAfterAvailableQty()).isEqualTo(200);
```

Also post `initialStock: -1` and expect HTTP 400. Add a Vue test asserting the create payload includes `initialStock` while update payload does not.

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
cd backend && mvn -Dtest=AdminCatalogControllerTest test
cd ../admin-web && pnpm test -- CatalogView.spec.ts
```

Expected: backend rejects/ignores `initialStock`; Vue has no stock control.

- [ ] **Step 3: Implement create-only stock initialization**

Create a flat `CreateBeadSkuRequest` with the same validated catalog fields and:

```java
@NotNull(message = "初始库存不能为空")
@PositiveOrZero(message = "初始库存不能为负数")
Integer initialStock
```

Expose `SaveBeadSkuRequest bead()` to convert catalog fields. Change only POST to consume this DTO. Inject both inventory mappers into `AdminCatalogService`, then after inserting the bead insert:

```java
InventoryBalance balance = new InventoryBalance();
balance.setBeadSkuId(bead.getId());
balance.setAvailableQty(request.initialStock());
balance.setReservedQty(0);
balance.setSoldQty(0);
balance.setVersion(0L);
inventoryBalanceMapper.insert(balance);
```

Insert an `INIT` ledger with before values zero, after available equal to initial stock, and note `后台创建商品初始化`. Keep the method `@Transactional` so any insert failure rolls back the SKU.

In Vue, define `CreateBeadSkuRequest = SaveBeadSkuRequest & { initialStock: number }`, show a non-negative integer field for create mode only, default 200, and omit it on PUT.

- [ ] **Step 4: Verify GREEN**

Run the two focused test commands from Step 2. Expected: both pass and rollback assertions remain green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/diybracelet/mall/catalog backend/src/test/java/com/diybracelet/mall/catalog/api/AdminCatalogControllerTest.java admin-web/src/api/catalog.ts admin-web/src/views/BeadCatalogPanel.vue admin-web/src/views/CatalogView.spec.ts
git commit -m "feat: initialize inventory when creating beads"
```

### Task 4: Crop and import three real demo products

**Files:**
- Create: `assets/demo-catalog/labradorite-8mm.png`
- Create: `assets/demo-catalog/garnet-8mm.png`
- Create: `assets/demo-catalog/fluorite-8mm.png`
- Create: `assets/demo-catalog/ai-water-guide.png`
- Create: `scripts/crop-demo-assets.sh`
- Create: `scripts/import-demo-beads.sh`
- Create: `scripts/demo-assets.test.sh`

**Interfaces:**
- Produces: four deterministic project assets and an idempotent importer keyed by SKU.
- Consumes: source images under `Pic/`, admin login, `/api/admin/files`, and `/api/admin/catalog/beads`.

- [ ] **Step 1: Write failing asset/import contract test**

`scripts/demo-assets.test.sh` must assert all output files exist, have non-zero size, are PNG, and the three product covers have equal dimensions. It also runs the importer with fake curl responses and asserts these exact SKU codes and stock 200 appear once.

- [ ] **Step 2: Run test and verify RED**

Run: `bash scripts/demo-assets.test.sh`

Expected: FAIL because derived assets and importer do not exist.

- [ ] **Step 3: Implement deterministic crops and importer**

Use `sips` against `Pic/530cd765-4340-4b78-a3f2-95bb3a4fb80d.png` with fixed cell crops:

```bash
sips --cropToHeightWidth 380 515 --cropOffset 0 0 "$source" --out assets/demo-catalog/labradorite-8mm.png
sips --cropToHeightWidth 380 515 --cropOffset 0 530 "$source" --out assets/demo-catalog/garnet-8mm.png
sips --cropToHeightWidth 380 515 --cropOffset 395 0 "$source" --out assets/demo-catalog/fluorite-8mm.png
```

Create a 512×512 derived entrance image from `Pic/32b3f736-c48a-446d-a800-230ee2d91579.png` using `sips -Z 512` without changing the original.

The importer logs in, lists categories and beads, skips an existing SKU, uploads the matching image, and POSTs the exact product table from the design with `categoryId=1001`, `diameterMm=8`, `initialStock=200`, `stockAlertThreshold=20`, and `status=ON_SALE`.

- [ ] **Step 4: Verify assets and real import**

Run:

```bash
bash scripts/crop-demo-assets.sh
bash scripts/demo-assets.test.sh
BACKEND_BASE_URL=http://localhost:8081 bash scripts/import-demo-beads.sh
curl -fsS 'http://localhost:8081/api/public/catalog/beads?page=1&size=100'
```

Expected: three SKUs and public image URLs are present; a second importer run reports all three as skipped.

- [ ] **Step 5: Commit**

```bash
git add assets/demo-catalog scripts/crop-demo-assets.sh scripts/import-demo-beads.sh scripts/demo-assets.test.sh
git commit -m "feat: import three photographed demo beads"
```

### Task 5: Persist and encrypt one DeepSeek configuration

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__ai_model_config.sql`
- Create: `backend/src/main/java/com/diybracelet/mall/recommendation/domain/AiModelConfig.java`
- Create: `backend/src/main/java/com/diybracelet/mall/recommendation/infrastructure/AiModelConfigMapper.java`
- Create: `backend/src/main/java/com/diybracelet/mall/recommendation/infrastructure/AiSecretCipher.java`
- Create: `backend/src/test/java/com/diybracelet/mall/recommendation/infrastructure/AiSecretCipherTest.java`
- Create: `backend/src/test/java/com/diybracelet/mall/recommendation/infrastructure/AiConfigurationMigrationTest.java`
- Modify: `.env.example`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Produces: singleton ID 1 and `encrypt(String)` / `decrypt(String)` AES-GCM methods.
- Consumes: base64-encoded 32-byte `AI_CONFIG_ENCRYPTION_KEY`.

- [ ] **Step 1: Write failing migration and cipher tests**

Test the seeded row equals provider `DEEPSEEK`, model `deepseek-v4-flash`, disabled, and has no encrypted key. Cipher test asserts two encryptions differ, both decrypt to the input, and a changed ciphertext throws a controlled exception.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd backend && mvn -Dtest=AiSecretCipherTest,AiConfigurationMigrationTest test`

Expected: test compilation fails because the migration and classes do not exist.

- [ ] **Step 3: Implement schema and AES-GCM**

Migration creates one row with optimistic `version`. `AiSecretCipher` uses `AES/GCM/NoPadding`, a random 12-byte nonce, 128-bit tag, and stores `base64(nonce + ciphertext)`. Validate the decoded master key is exactly 32 bytes before encrypting or decrypting.

Add:

```yaml
app:
  ai:
    encryption-key: ${AI_CONFIG_ENCRYPTION_KEY:}
```

Add an empty documented variable to `.env.example`; do not commit a real key.

- [ ] **Step 4: Verify GREEN**

Run the focused Maven test from Step 2. Expected: both tests pass.

- [ ] **Step 5: Commit**

```bash
git add .env.example backend/src/main/resources/application.yml backend/src/main/resources/db/migration/V6__ai_model_config.sql backend/src/main/java/com/diybracelet/mall/recommendation backend/src/test/java/com/diybracelet/mall/recommendation
git commit -m "feat: store encrypted DeepSeek configuration"
```

### Task 6: Add admin AI configuration and connection testing APIs

**Files:**
- Create: `backend/src/main/java/com/diybracelet/mall/recommendation/api/AdminAiConfigurationController.java`
- Create: DTOs under `backend/src/main/java/com/diybracelet/mall/recommendation/api/dto/`
- Create: `backend/src/main/java/com/diybracelet/mall/recommendation/application/AiConfigurationService.java`
- Create: `backend/src/main/java/com/diybracelet/mall/recommendation/domain/AiModelGateway.java`
- Create: `backend/src/main/java/com/diybracelet/mall/recommendation/infrastructure/DeepSeekModelGateway.java`
- Create: `backend/src/test/java/com/diybracelet/mall/recommendation/api/AdminAiConfigurationControllerTest.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/shared/error/ErrorCode.java`

**Interfaces:**
- Produces: GET/PUT/test/delete-key endpoints and `AiModelGateway.listModels(config)`.
- Consumes: singleton mapper and `AiSecretCipher`.

- [ ] **Step 1: Write failing controller tests**

Cover admin authorization, default masked view, saving without a key, saving a key without returning it, blank-key retention, explicit deletion, invalid non-HTTPS/foreign host, test success, 401, timeout, and unavailable model.

- [ ] **Step 2: Run test and verify RED**

Run: `cd backend && mvn -Dtest=AdminAiConfigurationControllerTest test`

Expected: test compilation fails because the controller and DTOs do not exist.

- [ ] **Step 3: Implement configuration service and DeepSeek `/models` gateway**

Use DTO contracts:

```java
public record AiConfigurationView(
        String provider, String baseUrl, String modelName,
        boolean apiKeyConfigured, boolean enabled,
        int timeoutSeconds, int maxOutputTokens, long version) {}

public record SaveAiConfigurationRequest(
        String baseUrl, String modelName, String apiKey,
        boolean enabled, int timeoutSeconds,
        int maxOutputTokens, long version) {}
```

Allow only scheme `https` and host `api.deepseek.com`. The gateway sends `Authorization: Bearer <decrypted key>` to `/models` with a per-request timeout. Add error codes with exact HTTP mappings: `AI_NOT_CONFIGURED` and `AI_DISABLED` → 503, `AI_UNAUTHORIZED` and `AI_MODEL_UNAVAILABLE` → 502, `AI_RATE_LIMITED` → 503, `AI_TEMPORARILY_UNAVAILABLE` → 503, and `AI_INVALID_RECOMMENDATION` → 422. Never include upstream response bodies.

- [ ] **Step 4: Verify GREEN**

Run the focused controller test. Expected: all admin configuration cases pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/diybracelet/mall/recommendation backend/src/main/java/com/diybracelet/mall/shared/error/ErrorCode.java backend/src/test/java/com/diybracelet/mall/recommendation
git commit -m "feat: manage and test DeepSeek configuration"
```

### Task 7: Add the Vue AI configuration page

**Files:**
- Create: `admin-web/src/api/ai.ts`
- Create: `admin-web/src/views/AiConfigurationView.vue`
- Create: `admin-web/src/views/AiConfigurationView.spec.ts`
- Modify: `admin-web/src/App.vue`
- Modify: `admin-web/src/style.css`

**Interfaces:**
- Produces: `getAiConfiguration`, `saveAiConfiguration`, `testAiConfiguration`, `clearAiApiKey`.
- Consumes: backend Task 6 endpoints and existing admin request/auth behavior.

- [ ] **Step 1: Write failing Vue tests**

Assert the view loads `deepseek-v4-flash`, renders only an empty password input plus “已配置/未配置”, sends blank key to preserve it, confirms explicit clearing, and displays distinct test-connection states.

- [ ] **Step 2: Run test and verify RED**

Run: `cd admin-web && pnpm test -- AiConfigurationView.spec.ts`

Expected: FAIL because the view does not exist.

- [ ] **Step 3: Implement page and navigation**

Add `ai` to `AdminView`, a “AI 模型” sidebar button, and a dedicated view. Fields are provider read-only, official base URL, model, optional password key, enabled switch, timeout 1–120, max output 256–16384, and version. Buttons call save/test/clear and show trace-aware errors.

- [ ] **Step 4: Verify GREEN and build**

Run: `cd admin-web && pnpm test -- AiConfigurationView.spec.ts && pnpm build`

Expected: tests, type-check, and Vite build pass.

- [ ] **Step 5: Commit**

```bash
git add admin-web/src/api/ai.ts admin-web/src/views/AiConfigurationView.vue admin-web/src/views/AiConfigurationView.spec.ts admin-web/src/App.vue admin-web/src/style.css
git commit -m "feat: add DeepSeek administration page"
```

### Task 8: Generate and validate one recommendation on the backend

**Files:**
- Create: recommendation request/response DTOs under `backend/src/main/java/com/diybracelet/mall/recommendation/api/dto/`
- Create: `backend/src/main/java/com/diybracelet/mall/recommendation/api/AppRecommendationController.java`
- Create: `backend/src/main/java/com/diybracelet/mall/recommendation/application/RecommendationService.java`
- Create: `backend/src/main/java/com/diybracelet/mall/recommendation/domain/RecommendationCandidate.java`
- Create: `backend/src/main/java/com/diybracelet/mall/recommendation/domain/ModelRecommendation.java`
- Create: `backend/src/main/java/com/diybracelet/mall/recommendation/infrastructure/RecommendationCandidateMapper.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/recommendation/domain/AiModelGateway.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/recommendation/infrastructure/DeepSeekModelGateway.java`
- Create: `backend/src/test/java/com/diybracelet/mall/recommendation/api/AppRecommendationControllerTest.java`
- Create: `backend/src/test/java/com/diybracelet/mall/recommendation/application/RecommendationServiceTest.java`

**Interfaces:**
- Produces: GET status, POST recommendation, and `AiModelGateway.recommend(config, profile, candidates)`.
- Consumes: customer JWT, sellable inventory candidates, `DiyPricingService.evaluate`, and current enabled cord option.

- [ ] **Step 1: Write failing service/controller tests**

Cover not configured, disabled, valid single result, unknown SKU, off-sale race, insufficient stock, non-positive quantity, over budget, non-quotable wrist size, malformed JSON, upstream error mapping, and the requirement that the final total comes from `DiyEvaluation`, not the model.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd backend && mvn -Dtest=RecommendationServiceTest,AppRecommendationControllerTest test`

Expected: compilation fails because recommendation classes do not exist.

- [ ] **Step 3: Implement candidate query, prompt contract, and authority checks**

The request contains optional birth date, optional manual constellation/zodiac, element preference, wish, scene, color preference, budget, wrist millimeters, fit type, and free text. Derive constellation and zodiac server-side when absent; never derive five elements. Convert birth date to the derived constellation/zodiac before building the gateway profile so the raw date is never sent to DeepSeek.

Candidate SQL joins `bead_sku` and `inventory_balance`, requiring `ON_SALE` and `available_qty > 0`. Send only controlled fields. Ask DeepSeek for `response_format: {"type":"json_object"}` with temperature 0.2 and this model shape:

```json
{
  "schemeName": "string",
  "sequence": [2001, 2002],
  "reasons": [{"skuId": 2001, "reason": "string"}],
  "summary": "string"
}
```

Validate membership, counts derived from sequence, current inventory, text lengths, and budget. Pick the first enabled cord option, call `DiyPricingService.evaluate`, require `quotable=true`, and return server amounts, product images, selected cord, and ordered sequence.

- [ ] **Step 4: Verify GREEN**

Run the focused Maven tests from Step 2. Expected: all success/error cases pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/diybracelet/mall/recommendation backend/src/test/java/com/diybracelet/mall/recommendation
git commit -m "feat: generate validated DeepSeek bracelet recommendations"
```

### Task 9: Add the mini-program consultation and DIY handoff

**Files:**
- Create: `miniprogram/miniprogram/services/recommendation.ts`
- Create: `miniprogram/miniprogram/domain/recommendation-profile.ts`
- Create: `miniprogram/src/recommendation-profile.spec.ts`
- Create: `miniprogram/miniprogram/pages/recommendation/index.ts`
- Create: `miniprogram/miniprogram/pages/recommendation/index.wxml`
- Create: `miniprogram/miniprogram/pages/recommendation/index.wxss`
- Create: `miniprogram/miniprogram/pages/recommendation/index.json`
- Modify: `miniprogram/miniprogram/pages/home/index.ts`
- Modify: `miniprogram/miniprogram/pages/home/index.wxml`
- Modify: `miniprogram/miniprogram/pages/home/index.wxss`
- Modify: `miniprogram/miniprogram/pages/diy/editor.ts`
- Modify: `miniprogram/miniprogram/app.json`
- Copy: `assets/demo-catalog/ai-water-guide.png` to `miniprogram/miniprogram/assets/images/ai-water-guide.png`

**Interfaces:**
- Produces: `deriveConstellation(date)`, `deriveChineseZodiac(date)`, status/recommend calls, and storage key `pending_ai_recommendation`.
- Consumes: Task 8 response and existing editor ordered SKU sequence.

- [ ] **Step 1: Write failing domain and handoff tests**

Test zodiac/constellation boundaries, leap dates, manual override precedence, validation of budget/wrist/free-text length, paused status copy, result rendering data, and conversion of the recommendation sequence into an editor draft.

- [ ] **Step 2: Run test and verify RED**

Run: `cd miniprogram && pnpm test -- recommendation-profile.spec.ts`

Expected: FAIL because the domain module does not exist.

- [ ] **Step 3: Implement form, status, result, and one-time handoff**

The home card navigates to `/pages/recommendation/index`. On load, fetch status. When unavailable, show the confirmed paused copy without a submit button. Form submission validates locally, calls POST, and renders one result with images, quantities, order, reasons, server price, and “文化娱乐参考”.

On “一键进入 DIY”:

```ts
wx.setStorageSync('pending_ai_recommendation', {
  sequence: result.sequence,
  wristCircumferenceMm: form.wristCircumferenceMm,
  fitType: form.fitType,
  cordOptionId: result.cordOption.id,
})
wx.navigateTo({ url: '/pages/diy/editor?source=ai' })
```

The editor reads this key only when `source=ai`, applies it as a new unsaved draft, then immediately removes the key.

- [ ] **Step 4: Verify GREEN**

Run:

```bash
cd miniprogram
pnpm test -- recommendation-profile.spec.ts diy-editor-state.spec.ts
pnpm exec tsc -p tsconfig.runtime.json
```

Expected: tests and type-check pass.

- [ ] **Step 5: Commit**

```bash
git add miniprogram/miniprogram miniprogram/src/recommendation-profile.spec.ts
git commit -m "feat: add mini program AI bracelet consultation"
```

### Task 10: End-to-end verification and living documentation

**Files:**
- Create: `scripts/recommendation-smoke.sh`
- Modify: `scripts/verify.sh`
- Modify: `README.md`
- Modify: `docs/setup.md`
- Modify: `docs/api.md`
- Modify: `docs/data-model.md`
- Modify: `docs/architecture.md`
- Modify: `docs/testing.md`
- Modify: `docs/requirements.md`
- Modify: `docs/progress.md`
- Modify: `docs/handoff.md`

**Interfaces:**
- Produces: repeatable verification for paused AI and configured DeepSeek paths.
- Consumes: all prior tasks.

- [ ] **Step 1: Write failing smoke assertions**

The smoke script must authenticate a guest, assert recommendation status, and when `DEEPSEEK_API_KEY` is supplied configure/test the model through admin APIs, request one recommendation, assert every returned SKU exists in the public catalog, and assert total amount is non-negative. Without the key it must assert `AI_NOT_CONFIGURED` or `AI_DISABLED` and must not claim a live model call.

- [ ] **Step 2: Run smoke test and verify RED**

Run: `BACKEND_BASE_URL=http://localhost:8081 bash scripts/recommendation-smoke.sh`

Expected: FAIL because the script does not exist.

- [ ] **Step 3: Implement smoke script and update documentation**

Add the shell test to `scripts/verify.sh` only for deterministic contract checks; keep the paid external call opt-in via `DEEPSEEK_API_KEY`. Update all listed documents with 8081, initial stock, DeepSeek configuration, security, API contracts, tests, progress evidence, and handoff steps.

- [ ] **Step 4: Run complete verification**

Run:

```bash
./scripts/verify.sh
BACKEND_BASE_URL=http://localhost:8081 ./scripts/catalog-smoke.sh
BACKEND_BASE_URL=http://localhost:8081 ./scripts/diy-smoke.sh
BACKEND_BASE_URL=http://localhost:8081 ./scripts/order-smoke.sh
BACKEND_BASE_URL=http://localhost:8081 ./scripts/recommendation-smoke.sh
git diff --check
git status --short
```

Expected: all deterministic tests and four HTTP smokes pass; only protected user changes remain outside committed work. A real DeepSeek call is additionally required after the user enters a valid API key in the new admin page.

- [ ] **Step 5: Commit**

```bash
git add scripts/verify.sh scripts/recommendation-smoke.sh README.md docs
git commit -m "docs: complete catalog and AI recommendation milestone"
```
