# Halve Bead Shadow Opacity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce the default DIY single-bead shadow opacity from `0.20` to `0.10` for existing and new installations without changing shadow geometry or bead rendering.

**Architecture:** Keep `beadShadowOpacity` as the single source of truth. A new Flyway migration updates the singleton visual configuration in existing databases, while the Vue administration page uses the same lighter value before its API request finishes. The mini-program renderer remains unchanged and continues consuming the configured value directly.

**Tech Stack:** MySQL 8, Flyway, Java 17, Spring Boot integration tests, Vue 3, TypeScript, Vitest.

## Global Constraints

- Change only single-bead shadow opacity from `0.20` to `0.10`.
- Do not change shadow radius, offset, color, bead overlap, bead size, image assets, or Canvas renderer math.
- Do not edit an already-applied Flyway migration.
- Preserve all unrelated uncommitted workspace changes.
- Write and observe each focused test failing before implementing its production change.

---

## File Structure

- Create `backend/src/main/resources/db/migration/V16__reduce_bead_shadow_opacity.sql` to update the persisted singleton visual configuration.
- Modify `backend/src/test/java/com/diybracelet/mall/catalog/infrastructure/CatalogMigrationTest.java` to verify the final migrated value and configuration version.
- Modify `admin-web/src/views/DiySettingsView.vue` to use `0.10` as its pre-load local default.
- Modify `admin-web/src/views/DiySettingsView.spec.ts` to verify the user-visible initial value before the API resolves and to align default API fixtures with `0.10`.

### Task 1: Migrate persisted shadow configuration

**Files:**
- Create: `backend/src/main/resources/db/migration/V16__reduce_bead_shadow_opacity.sql`
- Modify: `backend/src/test/java/com/diybracelet/mall/catalog/infrastructure/CatalogMigrationTest.java`

**Interfaces:**
- Consumes: singleton row `diy_visual_config.id = 1` created by Flyway V9.
- Produces: persisted `bead_shadow_opacity = 0.100` and `config_version = 2` after all migrations.

- [ ] **Step 1: Write the failing migration assertions**

Extend `shouldCreateManualVisualSchemaAndDefaultConfig()` with independently derived numeric expectations:

```java
assertThat(config.get("bead_shadow_opacity"))
        .isEqualTo(new BigDecimal("0.100"));
assertThat(config.get("config_version")).isEqualTo(2L);
```

Add `import java.math.BigDecimal;`. Replace the existing `config_version` expectation of `1L` with the `2L` expectation above.

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn -Dtest=CatalogMigrationTest test
```

Expected: FAIL because V9 still leaves `bead_shadow_opacity` at `0.200` and `config_version` at `1`.

- [ ] **Step 3: Add the minimal Flyway migration**

Create `V16__reduce_bead_shadow_opacity.sql` with:

```sql
update diy_visual_config
set bead_shadow_opacity = 0.100,
    config_version = config_version + 1,
    updated_by = 'migration-v16'
where id = 1;
```

This sets the approved absolute value rather than applying a hidden multiplier. Flyway executes the update once for both upgraded and fresh databases.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn -Dtest=CatalogMigrationTest test
```

Expected: PASS with the final row containing `0.100` and version `2`.

- [ ] **Step 5: Commit the backend change**

```bash
git add backend/src/main/resources/db/migration/V16__reduce_bead_shadow_opacity.sql \
  backend/src/test/java/com/diybracelet/mall/catalog/infrastructure/CatalogMigrationTest.java
git commit -m "fix: halve default bead shadow opacity"
```

### Task 2: Align the administration-page pre-load default

**Files:**
- Modify: `admin-web/src/views/DiySettingsView.vue`
- Modify: `admin-web/src/views/DiySettingsView.spec.ts`

**Interfaces:**
- Consumes: `DiyVisualConfig.beadShadowOpacity: number`.
- Produces: a visible pre-load default value of `0.1`; loaded and saved server values still replace it normally.

- [ ] **Step 1: Write the failing component test**

Add this focused test inside `describe('DiySettingsView', ...)`:

```typescript
it('starts with the lighter bead-shadow default while configuration loads', () => {
  vi.mocked(getAdminDiyConfiguration).mockImplementation(
    () => new Promise(() => undefined),
  )
  const wrapper = mount(DiySettingsView)
  const visualInputs = wrapper
    .findAll('section.settings-card')[1]
    .findAll<HTMLInputElement>('input[type="number"]')

  expect(visualInputs[1].element.value).toBe('0.1')
})
```

The second number input in the 2.5D settings card is the single-bead shadow opacity field.

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
cd admin-web
pnpm test -- DiySettingsView.spec.ts
```

Expected: FAIL with received value `0.2`.

- [ ] **Step 3: Implement the minimal default change**

In `DiySettingsView.vue`, change only:

```typescript
beadShadowOpacity: 0.2,
```

to:

```typescript
beadShadowOpacity: 0.1,
```

In `DiySettingsView.spec.ts`, update the three baseline configuration fixtures that represent the default server state from `0.2` to `0.1`, and update the `saves global 2.5D visual configuration` expectation to submit `beadShadowOpacity: 0.1`. Keep the explicit saved-response example `0.22` unchanged because it verifies that administrator-selected values still round-trip.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run:

```bash
cd admin-web
pnpm test -- DiySettingsView.spec.ts
```

Expected: all `DiySettingsView` tests PASS.

- [ ] **Step 5: Commit the administration-page change**

```bash
git add admin-web/src/views/DiySettingsView.vue \
  admin-web/src/views/DiySettingsView.spec.ts
git commit -m "fix: align DIY shadow default"
```

### Task 3: Verify the complete change

**Files:**
- Verify only; no production files should change.

**Interfaces:**
- Consumes: the migrated backend value and unchanged mini-program renderer contract.
- Produces: evidence that the related backend, administration UI, and Canvas tests pass.

- [ ] **Step 1: Run related backend tests**

Run:

```bash
cd backend
../apache-maven-3.9.16/bin/mvn -Dtest=CatalogMigrationTest,DiyConfigurationControllerTest test
```

Expected: PASS. `DiyConfigurationControllerTest` continues proving custom opacity values can be saved.

- [ ] **Step 2: Run the administration test suite and build**

Run:

```bash
cd admin-web
pnpm test
pnpm build
```

Expected: all tests PASS and the production build succeeds without TypeScript errors.

- [ ] **Step 3: Run the unchanged Canvas renderer contract test**

Run:

```bash
cd miniprogram
pnpm test -- bracelet-canvas-renderer.spec.ts
```

Expected: PASS, proving the renderer still consumes the supplied opacity without hidden scaling.

- [ ] **Step 4: Check the final diff**

Run:

```bash
git diff --check HEAD~2..HEAD
git status --short
```

Expected: no whitespace errors. Any unrelated pre-existing workspace changes remain untouched.
