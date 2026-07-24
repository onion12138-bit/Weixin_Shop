# Blender Bead Visual Worker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe asynchronous Blender pipeline that converts 3–6 administrator-labelled bead photos into reviewable fixed-view tray and Canvas WebP assets, while preserving the manual replacement path.

**Architecture:** The web application validates source roles and creates one durable generation job per visual version. A separate `visual-worker` Spring profile leases jobs from MySQL, copies registered assets into a per-job temporary directory, invokes a fixed Blender 4.5 LTS script with a fixed argument list, validates the outputs, stores them through the existing `FileStorage` abstraction, and transitions the visual version to `READY` or `FAILED`. The admin UI polls bounded job state and still allows manual replacement at every failure point.

**Tech Stack:** Java 17, Spring Boot 3.2.12, MyBatis-Plus, MySQL 8 row locking, Flyway, `ProcessBuilder`, Blender 4.5 LTS background Python, Vue 3.5, TypeScript 5.7, Vitest, Bash.

## Global Constraints

- Complete `2026-07-25-fixed-view-2.5d-manual-vertical-slice.md` and its completion gate before starting this plan.
- Blender is never invoked by an HTTP request thread; API calls only persist or requeue jobs.
- The worker is a separate process using Spring profile `visual-worker` and no HTTP server.
- Use Blender 4.5 LTS or a compatible newer LTS explicitly verified by the opt-in integration test.
- Invoke only a configured executable, a repository-owned Python script, and worker-created temporary paths.
- Never execute uploaded scripts, `.blend` files, node graphs, command fragments, output paths, or Python expressions.
- Accepted automatic source roles are exactly `FRONT`, `BACK`, `SIDE`, `SIDE_LEFT`, `SIDE_RIGHT`, and `DETAIL`.
- Every automatic version contains 3–6 distinct roles and must include `FRONT`, `BACK`, and `SIDE`.
- Accepted shape presets are exactly `ROUND`, `FACETED`, `BARREL`, and `SPACER`.
- Accepted material presets are exactly `OPAQUE_STONE`, `TRANSLUCENT_CRYSTAL`, `GLASS`, and `METAL`.
- Automatic output is fixed at 160×160 tray WebP and 512×512 Canvas WebP with alpha; Canvas output must not exceed 500 KB.
- Generation success produces `READY`, never `PUBLISHED`; an administrator still publishes explicitly.
- Automatic failure never blocks manual upload, catalog publication, DIY editing, pricing, orders, or fulfillment.
- Preserve all unrelated workspace changes and commit only the files listed by each task.
- Follow TDD and use a fake process executable for default tests; default verification must not require Blender.

---

## File Structure

- `backend/src/main/resources/db/migration/V10__bead_visual_generation_jobs.sql`: source images and durable job/lease schema.
- `backend/src/main/java/com/diybracelet/mall/catalog/visual/domain/`: source role, shape/material presets, job status/entity.
- `backend/src/main/java/com/diybracelet/mall/catalog/visual/infrastructure/`: source and job mappers.
- `backend/src/main/java/com/diybracelet/mall/catalog/visual/application/BeadVisualGenerationService.java`: automatic draft, queue, retry, and status transitions.
- `backend/src/main/java/com/diybracelet/mall/catalog/visual/worker/`: leasing, worker loop, Blender gateway, temporary files, and output persistence.
- `backend/src/main/resources/visuals/blender/render_bead.py`: fixed procedural scene/material renderer.
- `backend/src/main/resources/visuals/blender/presets.json`: auditable numeric preset values.
- `admin-web/src/components/BeadVisualPanel.vue`: source upload, preset selection, queue, polling, retry, and manual escape hatch.
- `scripts/blender-visual-integration.sh`: explicit real-Blender integration check.
- `scripts/start-visual-worker.sh`: local worker launcher with fixed profile.

---

### Task 1: Persist labelled source images and durable generation jobs

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__bead_visual_generation_jobs.sql`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/domain/BeadVisualSourceRole.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/domain/BeadShapePreset.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/domain/BeadMaterialPreset.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/domain/BeadVisualSource.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/domain/BeadVisualJobStatus.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/domain/BeadVisualJob.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/infrastructure/BeadVisualSourceMapper.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/infrastructure/BeadVisualJobMapper.java`
- Modify: `backend/src/test/java/com/diybracelet/mall/catalog/infrastructure/CatalogMigrationTest.java`

**Interfaces:**
- Produces: one unique job row per visual version.
- Produces: `BeadVisualJobMapper.selectNextPendingForUpdate()`.
- Produces: `BeadVisualSourceMapper.selectByVisualVersionId(long)`.
- Consumes: V9 `bead_visual_version` and `asset_file`.

- [ ] **Step 1: Add failing V10 migration assertions**

Extend `CatalogMigrationTest`:

```java
@Test
void shouldCreateVisualSourceAndJobLeaseSchema() {
    Integer sourceRoles = jdbcTemplate.queryForObject("""
        select count(*)
          from information_schema.columns
         where table_schema = database()
           and table_name = 'bead_visual_source'
           and column_name in ('visual_version_id','role','asset_file_id')
        """, Integer.class);
    assertThat(sourceRoles).isEqualTo(3);

    Integer jobColumns = jdbcTemplate.queryForObject("""
        select count(*)
          from information_schema.columns
         where table_schema = database()
           and table_name = 'bead_visual_job'
           and column_name in (
             'visual_version_id','status','attempt_count','max_attempts',
             'worker_id','lease_until','started_at','finished_at'
           )
        """, Integer.class);
    assertThat(jobColumns).isEqualTo(8);
}
```

- [ ] **Step 2: Run test and verify RED**

Run: `cd backend && mvn -Dtest=CatalogMigrationTest test`

Expected: FAIL because both tables are absent.

- [ ] **Step 3: Create the migration**

Use:

```sql
create table bead_visual_source (
    id bigint primary key auto_increment,
    visual_version_id bigint not null,
    role varchar(32) not null,
    asset_file_id bigint not null,
    sort_order int not null,
    created_at timestamp not null default current_timestamp,
    constraint uk_bead_visual_source_role
        unique (visual_version_id, role),
    constraint fk_bead_visual_source_version
        foreign key (visual_version_id)
        references bead_visual_version(id),
    constraint fk_bead_visual_source_asset
        foreign key (asset_file_id) references asset_file(id),
    index idx_bead_visual_source_version
        (visual_version_id, sort_order)
) engine = InnoDB default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci;

create table bead_visual_job (
    id bigint primary key auto_increment,
    visual_version_id bigint not null,
    status varchar(32) not null,
    attempt_count int not null default 0,
    max_attempts int not null default 3,
    worker_id varchar(120) null,
    lease_until timestamp null,
    started_at timestamp null,
    finished_at timestamp null,
    failure_code varchar(64) null,
    failure_message varchar(500) null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
        on update current_timestamp,
    constraint uk_bead_visual_job_version unique (visual_version_id),
    constraint fk_bead_visual_job_version
        foreign key (visual_version_id)
        references bead_visual_version(id),
    constraint chk_bead_visual_job_attempts
        check (attempt_count between 0 and max_attempts),
    index idx_bead_visual_job_claim
        (status, lease_until, id)
) engine = InnoDB default charset = utf8mb4
  collate = utf8mb4_0900_ai_ci;
```

- [ ] **Step 4: Implement enums, entities, and mapper queries**

Enums:

```java
public enum BeadVisualSourceRole {
    FRONT, BACK, SIDE, SIDE_LEFT, SIDE_RIGHT, DETAIL
}
public enum BeadShapePreset { ROUND, FACETED, BARREL, SPACER }
public enum BeadMaterialPreset {
    OPAQUE_STONE, TRANSLUCENT_CRYSTAL, GLASS, METAL
}
public enum BeadVisualJobStatus {
    PENDING, RUNNING, SUCCEEDED, RETRYABLE_FAILED, TERMINAL_FAILED
}
```

The claim mapper uses MySQL 8 locking:

```java
@Select("""
    select * from bead_visual_job
     where (
       status = 'PENDING'
       or (
         status = 'RUNNING'
         and lease_until < current_timestamp
       )
     )
       and attempt_count < max_attempts
     order by id
     limit 1
     for update skip locked
    """)
BeadVisualJob selectNextPendingForUpdate();
```

- [ ] **Step 5: Verify GREEN**

Run:

```bash
cd backend
mvn -Dtest=CatalogMigrationTest,CatalogMapperTest test
```

Expected: PASS and Flyway reaches V10.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V10__bead_visual_generation_jobs.sql \
  backend/src/main/java/com/diybracelet/mall/catalog/visual/domain \
  backend/src/main/java/com/diybracelet/mall/catalog/visual/infrastructure \
  backend/src/test/java/com/diybracelet/mall/catalog/infrastructure/CatalogMigrationTest.java
git commit -m "feat: persist bead visual generation jobs"
```

### Task 2: Create automatic drafts, queue generation, and retry failures

**Files:**
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/api/dto/CreateAutomaticBeadVisualRequest.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/api/dto/BeadVisualJobView.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/application/BeadVisualGenerationService.java`
- Create: `backend/src/test/java/com/diybracelet/mall/catalog/visual/api/AdminBeadVisualGenerationControllerTest.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/catalog/visual/api/AdminBeadVisualController.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/catalog/visual/api/dto/BeadVisualHistoryView.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/catalog/visual/api/dto/BeadVisualVersionView.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/shared/error/ErrorCode.java`

**Interfaces:**
- Produces: `POST /api/admin/catalog/beads/{skuId}/visuals/auto`.
- Produces: `POST /api/admin/catalog/beads/{skuId}/visuals/{versionId}/generate`.
- Produces: `POST /api/admin/catalog/beads/{skuId}/visuals/{versionId}/retry`.
- Produces: job summary in visual history responses.

- [ ] **Step 1: Write failing request validation tests**

POST this valid contract:

```json
{
  "shapePreset": "ROUND",
  "materialPreset": "TRANSLUCENT_CRYSTAL",
  "sources": [
    {"role":"FRONT","assetFileId":911},
    {"role":"BACK","assetFileId":912},
    {"role":"SIDE","assetFileId":913}
  ]
}
```

Expect `201`, `sourceType=AUTO`, `status=DRAFT`, and version 1.

Add invalid cases:

- only two sources;
- duplicate `FRONT`;
- missing `BACK`;
- seven sources;
- inactive/non-image asset;
- preset not in the enum.

Every invalid request expects HTTP 400 and no version/job rows.

- [ ] **Step 2: Write failing queue and idempotency tests**

Call `/generate` twice and assert:

```java
assertThat(jobMapper.selectCount(
    Wrappers.lambdaQuery(BeadVisualJob.class)
        .eq(BeadVisualJob::getVisualVersionId, versionId)
)).isEqualTo(1);
assertThat(versionMapper.selectById(versionId).getStatus())
    .isEqualTo(BeadVisualStatus.GENERATING);
```

Set a version/job to terminal failure, call `/retry`, and assert the same job ID becomes `PENDING`, clears lease/failure fields, and the version becomes `GENERATING`. Reject retry for `READY`, `PUBLISHED`, and `MANUAL` versions.

- [ ] **Step 3: Run tests and verify RED**

Run:

```bash
cd backend
mvn -Dtest=AdminBeadVisualGenerationControllerTest test
```

Expected: compilation fails because automatic generation endpoints do not exist.

- [ ] **Step 4: Implement exact request records**

```java
public record CreateAutomaticBeadVisualRequest(
    @NotNull BeadShapePreset shapePreset,
    @NotNull BeadMaterialPreset materialPreset,
    @NotEmpty @Size(min = 3, max = 6)
    List<SourceInput> sources
) {
    public record SourceInput(
        @NotNull BeadVisualSourceRole role,
        @NotNull @Positive Long assetFileId
    ) {}
}
```

Service validation explicitly requires `FRONT`, `BACK`, and `SIDE`, distinct roles, active PNG/JPEG/WebP assets, and `source_type=AUTO`.

- [ ] **Step 5: Implement queue/retry transitions**

`queue(versionId)`:

```java
requireAutoVersion(version);
requireStatus(version, BeadVisualStatus.DRAFT);
insertJobIfAbsent(version.getId(), maxAttempts);
version.setStatus(BeadVisualStatus.GENERATING);
versionMapper.updateById(version);
```

If a job already exists and is `PENDING` or `RUNNING`, return it unchanged. Any other duplicate state is a conflict except the explicit retry path.

`retry(versionId)` only accepts `AUTO/FAILED` with `attemptCount < maxAttempts`. It clears `workerId`, `leaseUntil`, `finishedAt`, and failure fields, sets job `PENDING`, and sets the version `GENERATING`.

- [ ] **Step 6: Verify GREEN**

Run:

```bash
cd backend
mvn -Dtest=AdminBeadVisualGenerationControllerTest,AdminBeadVisualControllerTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/diybracelet/mall/catalog/visual \
  backend/src/main/java/com/diybracelet/mall/shared/error/ErrorCode.java \
  backend/src/test/java/com/diybracelet/mall/catalog/visual/api/AdminBeadVisualGenerationControllerTest.java
git commit -m "feat: queue automatic bead visual generation"
```

### Task 3: Lease jobs in a non-web worker profile

**Files:**
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/worker/VisualGenerationGateway.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/worker/GeneratedVisualAssets.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/worker/VisualGenerationInput.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/worker/VisualJobLeaseService.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/worker/VisualJobProcessor.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/worker/VisualWorkerLoop.java`
- Create: `backend/src/test/java/com/diybracelet/mall/catalog/visual/worker/VisualJobLeaseServiceTest.java`
- Create: `backend/src/test/java/com/diybracelet/mall/catalog/visual/worker/VisualJobProcessorTest.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/file/application/FileStorageService.java`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Produces: `VisualGenerationGateway.generate(VisualGenerationInput): GeneratedVisualAssets`.
- Produces: `FileStorageService.storeGenerated(String, String, byte[])`.
- Consumes: V10 job/source mappers and V9 asset validator.

- [ ] **Step 1: Write failing lease tests**

Use two concurrent transactions and assert each receives a different pending job. Add:

```java
VisualJobLease first = leaseService
    .claim("worker-a", Duration.ofMinutes(5))
    .orElseThrow();
assertThat(first.workerId()).isEqualTo("worker-a");
assertThat(first.attempt()).isEqualTo(1);
assertThat(jobMapper.selectById(first.jobId()).getStatus())
    .isEqualTo(BeadVisualJobStatus.RUNNING);
```

Expire a RUNNING lease and assert it can be reclaimed with attempt 2. A live lease must not be reclaimed.

- [ ] **Step 2: Write failing processor transition tests**

With a fake gateway returning valid bytes, assert:

- two new `asset_file` rows are written;
- visual becomes `READY`;
- job becomes `SUCCEEDED`;
- no publication pointer changes.

With `RetryableVisualGenerationException`, assert retryable failure becomes
`RETRYABLE_FAILED` when attempts remain. Call
`leaseService.requeueRetryableFailures()` and assert it becomes `PENDING`.
With invalid output or exhausted attempts, assert version becomes `FAILED`
and job becomes `TERMINAL_FAILED`.

- [ ] **Step 3: Run tests and verify RED**

Run:

```bash
cd backend
mvn -Dtest=VisualJobLeaseServiceTest,VisualJobProcessorTest test
```

Expected: FAIL because worker interfaces and services are absent.

- [ ] **Step 4: Add generated-byte storage**

Refactor `FileStorageService.store(MultipartFile)` to delegate to:

```java
@Transactional
public UploadedFileResponse storeGenerated(
    String originalName,
    String contentType,
    byte[] content
) {
    StoredFile stored = fileStorage.store(originalName, contentType, content);
    return persist(stored);
}
```

The HTTP upload path keeps all current validation. The worker path still passes through `FileStorage`, safe file names, MIME allowlist, size bound, and `asset_file`.

- [ ] **Step 5: Implement lease and terminal transitions**

`claim` runs in a short `REQUIRES_NEW` transaction:

1. select next row `for update skip locked`;
2. set `RUNNING`, `workerId`, `leaseUntil`, `startedAt` if null;
3. increment `attemptCount`;
4. clear old failure fields;
5. return an immutable lease.

The expensive gateway call occurs outside the lease transaction. Completion methods lock the job/version again and verify `workerId` still owns the unexpired lease before changing state.

`requeueRetryableFailures()` runs in a short transaction before each polling
claim. It changes only `RETRYABLE_FAILED` rows with attempts remaining to
`PENDING`; it leaves failure code/message visible until the next claim clears
them.

Define the gateway input in this task so the fake processor compiles before the
Blender implementation:

```java
public record VisualGenerationInput(
    long visualVersionId,
    BeadShapePreset shapePreset,
    BeadMaterialPreset materialPreset,
    Map<BeadVisualSourceRole, Path> sources
) {}
```

- [ ] **Step 6: Add the profile-bounded loop**

```java
@Component
@Profile("visual-worker")
public final class VisualWorkerLoop {
    @Scheduled(fixedDelayString =
        "${app.visual-worker.poll-delay-ms:2000}")
    public void pollOnce() {
        leaseService.requeueRetryableFailures();
        leaseService.claim(workerId, leaseDuration)
            .ifPresent(processor::process);
    }
}
```

Run the process with:

```text
SPRING_PROFILES_ACTIVE=visual-worker
SPRING_MAIN_WEB_APPLICATION_TYPE=none
```

Set all worker defaults under `app.visual-worker`; no worker bean runs in the normal web profile.

- [ ] **Step 7: Verify GREEN**

Run:

```bash
cd backend
mvn -Dtest=VisualJobLeaseServiceTest,VisualJobProcessorTest,FileControllerTest test
```

Expected: PASS; default tests use only a fake gateway.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/diybracelet/mall/catalog/visual/worker \
  backend/src/main/java/com/diybracelet/mall/file/application/FileStorageService.java \
  backend/src/main/resources/application.yml \
  backend/src/test/java/com/diybracelet/mall/catalog/visual/worker \
  backend/src/test/java/com/diybracelet/mall/file/api/FileControllerTest.java
git commit -m "feat: process visual jobs in leased worker"
```

### Task 4: Invoke a fixed Blender renderer with bounded temporary files

**Files:**
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/worker/BlenderVisualGenerationGateway.java`
- Create: `backend/src/main/java/com/diybracelet/mall/catalog/visual/worker/VisualOutputValidator.java`
- Create: `backend/src/main/resources/visuals/blender/render_bead.py`
- Create: `backend/src/main/resources/visuals/blender/presets.json`
- Create: `backend/src/test/java/com/diybracelet/mall/catalog/visual/worker/BlenderVisualGenerationGatewayTest.java`
- Create: `backend/src/test/java/com/diybracelet/mall/catalog/visual/worker/VisualOutputValidatorTest.java`
- Modify: `.env.example`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Implements: `VisualGenerationGateway`.
- Consumes: fixed `Path blenderExecutable`, fixed script resource, worker temp root, structured source files.
- Produces: alpha WebP bytes at exactly 160×160 and 512×512.

- [ ] **Step 1: Write failing fixed-command tests**

Use a fake executable shell script that records arguments and writes fixture outputs. Assert the gateway command list equals:

```text
<configured blender>
--background
--factory-startup
--disable-autoexec
--python
<worker-copied render_bead.py>
--python-exit-code
20
--
<worker-created job.json>
```

Assert an uploaded original filename such as `"; touch escaped"` never appears in any command argument or temp path.

- [ ] **Step 2: Write failing timeout and output tests**

Test:

- process timeout forcibly destroys the child and raises retryable `VISUAL_BLENDER_TIMEOUT`;
- non-zero exit maps to a bounded, sanitized failure message;
- missing output is terminal `VISUAL_OUTPUT_MISSING`;
- wrong dimensions, no alpha, or Canvas file over 500 KB are terminal;
- temporary directory is removed in success and failure paths.

- [ ] **Step 3: Run tests and verify RED**

Run:

```bash
cd backend
mvn -Dtest=BlenderVisualGenerationGatewayTest,VisualOutputValidatorTest test
```

Expected: FAIL because the gateway and renderer are absent.

- [ ] **Step 4: Implement structured input and safe process execution**

The gateway:

1. creates `Files.createTempDirectory(configuredRoot, "visual-job-")`;
2. copies each registered source to a generated name such as `front.png`;
3. writes a Jackson JSON file containing enum names and generated relative paths;
4. builds a `List<String>` command, never a shell command;
5. redirects stdout/stderr to bounded worker-owned files;
6. waits at most the configured timeout;
7. validates outputs;
8. reads bytes;
9. recursively deletes only the exact task temp directory in `finally`.

- [ ] **Step 5: Implement the fixed Blender Python script**

The script must:

- parse only the JSON path after `--`;
- reject unknown keys and enum values;
- reset to factory scene;
- construct one of four procedural meshes;
- use `FRONT` as the camera-facing color texture;
- sample BACK/SIDE images only for bounded average color/material calibration;
- apply numeric values from repository-owned `presets.json`;
- use a fixed orthographic camera, world color, key light, fill light, and rim light;
- render with transparent film;
- output 512×512 RGBA WebP and a separately rendered/resized 160×160 RGBA WebP;
- never import Python modules from source directories;
- never load uploaded `.blend` or Python files.

The preset file contains explicit values:

```json
{
  "OPAQUE_STONE": {"metallic":0.0,"roughness":0.32,"transmission":0.0},
  "TRANSLUCENT_CRYSTAL": {"metallic":0.0,"roughness":0.18,"transmission":0.28},
  "GLASS": {"metallic":0.0,"roughness":0.08,"transmission":0.55},
  "METAL": {"metallic":0.85,"roughness":0.22,"transmission":0.0}
}
```

These are visual approximations, not claims of physical material accuracy.

- [ ] **Step 6: Verify GREEN with the fake executable**

Run:

```bash
cd backend
mvn -Dtest=BlenderVisualGenerationGatewayTest,VisualOutputValidatorTest,VisualJobProcessorTest test
```

Expected: PASS without Blender installed.

- [ ] **Step 7: Commit**

```bash
git add .env.example backend/src/main/resources/application.yml \
  backend/src/main/resources/visuals/blender \
  backend/src/main/java/com/diybracelet/mall/catalog/visual/worker \
  backend/src/test/java/com/diybracelet/mall/catalog/visual/worker
git commit -m "feat: render bead visuals with fixed blender script"
```

### Task 5: Add automatic generation and bounded polling to the admin panel

**Files:**
- Modify: `admin-web/src/api/catalog.ts`
- Modify: `admin-web/src/api/catalog.spec.ts`
- Modify: `admin-web/src/components/BeadVisualPanel.vue`
- Modify: `admin-web/src/components/BeadVisualPanel.spec.ts`
- Modify: `admin-web/src/style.css`

**Interfaces:**
- Consumes: automatic draft, generate, retry, and visual history endpoints.
- Produces: one admin workflow that always retains the manual replacement controls.

- [ ] **Step 1: Write failing API tests**

Assert:

```ts
await createAutomaticBeadVisual(2001, {
  shapePreset: 'ROUND',
  materialPreset: 'TRANSLUCENT_CRYSTAL',
  sources: [
    { role: 'FRONT', assetFileId: 911 },
    { role: 'BACK', assetFileId: 912 },
    { role: 'SIDE', assetFileId: 913 },
  ],
})
```

uses `/admin/catalog/beads/2001/visuals/auto`, and generation/retry use their exact version paths.

- [ ] **Step 2: Write failing UI tests**

Cover:

- exactly three required source slots are visible initially;
- optional source rows can be added up to six;
- duplicate role is blocked locally;
- source files are uploaded before automatic draft creation;
- after draft creation, generate is called once;
- polling runs every 2 seconds while `GENERATING`;
- polling stops at `READY`, `FAILED`, component unmount, or after 5 minutes;
- `READY` still requires explicit publish;
- `FAILED` exposes both “重试自动生成” and “改用人工上传”;
- manual controls never disappear.

Use fake timers:

```ts
vi.useFakeTimers()
await wrapper.get('[data-test="generate-visual"]').trigger('click')
await vi.advanceTimersByTimeAsync(2_000)
expect(listBeadVisuals).toHaveBeenCalledTimes(2)
wrapper.unmount()
await vi.advanceTimersByTimeAsync(2_000)
expect(listBeadVisuals).toHaveBeenCalledTimes(2)
```

- [ ] **Step 3: Run tests and verify RED**

Run:

```bash
cd admin-web
pnpm test -- api/catalog.spec.ts components/BeadVisualPanel.spec.ts
```

Expected: FAIL because automatic types and controls are absent.

- [ ] **Step 4: Implement automatic API contracts**

Add exact union types for source roles, shape presets, material presets, and job status. Extend `BeadVisualVersion` with:

```ts
shapePreset: BeadShapePreset | null
materialPreset: BeadMaterialPreset | null
job: {
  id: number
  status: BeadVisualJobStatus
  attemptCount: number
  maxAttempts: number
  failureCode: string | null
  failureMessage: string | null
} | null
```

- [ ] **Step 5: Implement upload, queue, polling, retry, and escape hatch**

Keep state boundaries explicit:

- `sourceDrafts`: local files and roles;
- `creatingAutomatic`: uploads and creates the version;
- `queueing`: calls generate;
- `pollTimer`: one owned timer cleared before replacement and on unmount;
- `manualDraft`: unchanged from the first plan.

Do not auto-publish after `READY`.

- [ ] **Step 6: Verify GREEN and build**

Run:

```bash
cd admin-web
pnpm test -- api/catalog.spec.ts components/BeadVisualPanel.spec.ts
pnpm build
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add admin-web/src/api/catalog.ts admin-web/src/api/catalog.spec.ts \
  admin-web/src/components/BeadVisualPanel.vue \
  admin-web/src/components/BeadVisualPanel.spec.ts \
  admin-web/src/style.css
git commit -m "feat: generate bead visuals from admin"
```

### Task 6: Package, document, and opt-in test the real worker

**Files:**
- Create: `scripts/start-visual-worker.sh`
- Create: `scripts/blender-visual-integration.sh`
- Create: `scripts/blender-visual-static.test.sh`
- Modify: `scripts/check-prerequisites.sh`
- Modify: `scripts/start-local.sh`
- Modify: `scripts/start-local.test.sh`
- Modify: `scripts/verify.sh`
- Modify: `docs/api.md`
- Modify: `docs/architecture.md`
- Modify: `docs/data-model.md`
- Modify: `docs/deployment.md`
- Modify: `docs/development.md`
- Modify: `docs/progress.md`
- Modify: `docs/testing.md`

**Interfaces:**
- Produces: explicit local worker launcher and opt-in real Blender test.
- Consumes: `BLENDER_EXECUTABLE`, source fixtures, backend profile configuration.

- [ ] **Step 1: Write failing static script tests**

`blender-visual-static.test.sh` asserts:

- worker launcher sets `SPRING_PROFILES_ACTIVE=visual-worker`;
- worker launcher sets `SPRING_MAIN_WEB_APPLICATION_TYPE=none`;
- integration script checks `Blender 4.5`;
- integration script uses `--background`, `--disable-autoexec`, `--python-exit-code`;
- default `verify.sh` does not invoke Blender or create generation jobs.

Run: `bash scripts/blender-visual-static.test.sh`

Expected: FAIL because the scripts are absent.

- [ ] **Step 2: Implement the local worker launcher**

`start-visual-worker.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "${project_root}/scripts/project-env.sh"
: "${BLENDER_EXECUTABLE:?set BLENDER_EXECUTABLE to Blender 4.5 LTS}"
export SPRING_PROFILES_ACTIVE=visual-worker
export SPRING_MAIN_WEB_APPLICATION_TYPE=none
cd "${project_root}/backend"
exec mvn spring-boot:run
```

`start-local.sh` starts the worker only when `START_VISUAL_WORKER=1`; default local startup remains unchanged.

- [ ] **Step 3: Implement the opt-in real Blender test**

The integration script:

1. checks executable version;
2. creates a temporary task directory;
3. copies repository test source photos;
4. invokes the fixed renderer;
5. asserts both WebP files exist, have non-zero size, and stay under 500 KB;
6. calls the backend `VisualOutputValidator` integration harness or a dedicated test main to assert exact dimensions and alpha;
7. cleans only its own `mktemp -d` directory.

Require explicit opt-in:

```bash
: "${BLENDER_EXECUTABLE:?real Blender test is opt-in}"
```

- [ ] **Step 4: Update prerequisites and operational documentation**

Document:

- supported Blender version;
- worker environment variables, process isolation, timeout, leases, retries;
- fixed source roles/presets;
- how to start/stop the worker without stopping the web backend;
- how to diagnose sanitized failure codes;
- manual replacement recovery;
- no-auto-publish rule;
- production resource limits and temp/storage permissions.

- [ ] **Step 5: Run default verification**

Run:

```bash
bash scripts/blender-visual-static.test.sh
./scripts/verify.sh
```

Expected: PASS without Blender installed or configured.

- [ ] **Step 6: Run opt-in Blender integration**

Run:

```bash
BLENDER_EXECUTABLE=/absolute/path/to/blender \
bash scripts/blender-visual-integration.sh
```

Expected:

```text
Blender version accepted
512x512 alpha WebP accepted
160x160 alpha WebP accepted
temporary directory cleaned
```

- [ ] **Step 7: Run one end-to-end admin job**

With backend and worker running:

1. create an automatic version for a demo bead;
2. confirm status `DRAFT`;
3. queue it and confirm `GENERATING`;
4. wait for `READY`;
5. inspect both previews;
6. publish explicitly;
7. open the mini program and confirm the new fixed-view asset appears;
8. stop the worker, create another auto version, and confirm manual upload remains available.

- [ ] **Step 8: Commit**

```bash
git add scripts/start-visual-worker.sh scripts/blender-visual-integration.sh \
  scripts/blender-visual-static.test.sh scripts/check-prerequisites.sh \
  scripts/start-local.sh scripts/start-local.test.sh scripts/verify.sh \
  docs/api.md docs/architecture.md docs/data-model.md docs/deployment.md \
  docs/development.md docs/progress.md docs/testing.md
git commit -m "docs: operate blender bead visual worker"
```

---

## Completion Gate

The complete recommendation is delivered only when:

- both implementation plans pass `./scripts/verify.sh`;
- default tests remain Blender-independent;
- a real Blender 4.5 LTS opt-in test produces valid alpha WebP outputs;
- the worker can recover an expired lease without duplicate published assets;
- automatic generation ends at `READY`, never automatic publication;
- a failed automatic version can be replaced manually and published;
- the mini program continues to render the manual-plan fallback chain with the worker stopped.
