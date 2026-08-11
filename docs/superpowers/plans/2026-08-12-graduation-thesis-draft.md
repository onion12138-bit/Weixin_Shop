# Graduation Thesis Draft Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a complete, editable, template-compliant Word first draft for “基于SpringBoot的微信小程序DIY商品系统设计与实现” using verified project evidence.

**Architecture:** Keep research evidence, thesis prose, figures, document assembly, and verification in separate files. Build the final DOCX from the read-only converted university template, then validate OOXML structure, content coverage, and every rendered page before delivery.

**Tech Stack:** Bundled Python 3, python-docx, OOXML, LibreOffice, Poppler, Graphviz or Pillow for diagrams, project Java/TypeScript/Jest/Vitest evidence, and primary/academic web sources.

## Global Constraints

- The final deliverable is `/Users/onion/Desktop/AI_Testing/Weixin_Shop/项目进程文件/基于SpringBoot的微信小程序DIY商品系统设计与实现_毕业论文初稿.docx`.
- The source template `/Users/onion/Desktop/qqw/上海大学继教毕业设计(论文)模版-26新版.doc` and converted baseline `/private/tmp/weixin_shop_thesis_20260812/template/上海大学继教毕业设计(论文)模版-26新版.docx` remain unchanged.
- Use A4 portrait, 2.5 cm top/bottom margins, 3.0 cm left margin, 2.0 cm right margin, Songti 12 pt Chinese body, Times New Roman 12 pt western body, 1.5 line spacing, and two-character first-line indentation.
- Preserve the cover, statement, pledge, university identity, header, footer, and page-number system; remove instructional balloons and example prose.
- Cover identity fields use the exact temporary value `待填写`.
- Use the eight-chapter structure approved in `docs/superpowers/specs/2026-08-12-graduation-thesis-draft-design.md`.
- Main Chinese body text must contain at least 12000 Chinese characters, excluding abstracts, table of contents, acknowledgements, references, and appendices.
- Include at least 15 verified references, at least 2 English references, with matching in-text citations.
- Do not claim that WeChat authorization, real payment/refund, object storage, live logistics, Redis deployment, production-scale performance, or the pending DeepSeek live success path is complete.
- Distinguish the 2026-08-09 historical full verification from the 2026-08-12 Docker/Testcontainers environment blockage.
- Do not copy substantial passages from published works; write project-specific original analysis and cite summarized technical facts.
- Use only the bundled document runtime at `/Users/onion/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3`.

---

## File Map

- Create `项目进程文件/论文初稿工作/evidence_manifest.md`: auditable project facts, test results, current limitations, and provenance.
- Create `项目进程文件/论文初稿工作/reference_manifest.json`: verified bibliography metadata and citation keys.
- Create `项目进程文件/论文初稿工作/thesis_content.py`: structured original prose, captions, table records, and in-text citation keys.
- Create `项目进程文件/论文初稿工作/thesis_figures.py`: deterministic architecture, flow, module, and ER diagrams.
- Create `项目进程文件/论文初稿工作/build_thesis.py`: template-based DOCX assembly and OOXML field/header/footer handling.
- Create `项目进程文件/论文初稿工作/validate_thesis.py`: word-count, citation, heading, page-geometry, image, and sensitive-claim audits.
- Create `项目进程文件/论文初稿工作/tests/test_thesis_content.py`: prose coverage and factual-boundary tests.
- Create `项目进程文件/论文初稿工作/tests/test_thesis_docx.py`: final DOCX structural and formatting tests.
- Create `项目进程文件/论文初稿工作/figures/*.png`: generated diagrams and selected current screenshots.
- Create `项目进程文件/论文初稿工作/qa-render/page-*.png`: internal render artifacts, not user-facing deliverables.
- Create `项目进程文件/基于SpringBoot的微信小程序DIY商品系统设计与实现_毕业论文初稿.docx`: final deliverable.

### Task 1: Freeze the project evidence set

**Files:**
- Create: `项目进程文件/论文初稿工作/evidence_manifest.md`
- Read: `docs/progress.md`
- Read: `docs/requirements.md`
- Read: `backend/src/main/resources/db/migration/*.sql`
- Read: `backend/src/main/java/**/*.java`
- Read: `admin-web/src/**/*`
- Read: `miniprogram/**/*`

**Interfaces:**
- Consumes: current repository state and prior verification outputs.
- Produces: factual statements labeled `implemented`, `historical-pass`, `current-pass`, `environment-blocked`, or `future-work`.

- [ ] **Step 1: Record repository and implementation facts**

  Run read-only Git, file-count, migration, page, route, controller, service, and test discovery commands. Record commit `d044d87` as the latest implementation commit observed before thesis generation and record later thesis-only commits separately.

- [ ] **Step 2: Record the exact verification boundary**

  Include these exact facts: current backend compilation covers 187 production sources and 37 test sources; Maven reports 185 tests with 16 environment errors caused by the unavailable Docker socket; admin has 11 test files and 92 passing tests plus passing type-check/build; miniprogram has 26 suites and 192 passing tests plus passing runtime type-check.

- [ ] **Step 3: Classify every feature claim**

  Mark product/category/SKU, DIY sequence, 2.5D editor, authoritative quotation, schemes, customer profile/address/favorite/coupon, recommendations, inventory, restocking, orders, mock payment, fulfillment, operation logs, AI configuration, encrypted API key, and AI logs as implemented. Mark real WeChat login/payment/refund, object storage, live logistics, Redis deployment, and pending production DeepSeek success as future work.

- [ ] **Step 4: Audit the manifest**

  Search for contradictory pairs such as “已接入真实支付” and “模拟支付”; the manifest passes only when every factual claim has one status and one repository/doc source.

### Task 2: Verify and normalize references

**Files:**
- Create: `项目进程文件/论文初稿工作/reference_manifest.json`
- Read: `项目进程文件/开题报告.docx`
- Read: `项目进程文件/任务书.docx`

**Interfaces:**
- Consumes: bibliography candidates from the approved documents plus authoritative primary sources.
- Produces: JSON records with `key`, `authors`, `title`, `year`, `type`, `publisher_or_journal`, `volume`, `issue`, `pages`, `url_or_doi`, `language`, `verified_url`, and `used_in_sections`.

- [ ] **Step 1: Verify each opening-report bibliography candidate**

  Use official publisher/journal/catalog pages, Crossref/DOI pages, or the publication's own site. Exclude any item whose authorship, title, year, issue, or publication cannot be verified.

- [ ] **Step 2: Add authoritative technical sources**

  Add current official Spring Boot, Vue, TypeScript, MySQL, Flyway, WeChat Mini Program, JWT/RFC, and OWASP sources where they support the actual technology discussion. Store access date `2026-08-12` for online documentation.

- [ ] **Step 3: Enforce bibliography requirements**

  Validate at least 15 records, at least 2 English records, at least 10 academic journal articles where verifiable, and a majority published in 2022—2026. No record may have an empty verification URL.

- [ ] **Step 4: Map citations to thesis sections**

  Assign each retained source to at least one of chapters 1—7. Exclude sources that would appear only in the reference list without supporting prose.

### Task 3: Create the diagram and screenshot evidence set

**Files:**
- Create: `项目进程文件/论文初稿工作/thesis_figures.py`
- Create: `项目进程文件/论文初稿工作/figures/figure-3-1-use-cases.png`
- Create: `项目进程文件/论文初稿工作/figures/figure-3-2-diy-order-flow.png`
- Create: `项目进程文件/论文初稿工作/figures/figure-4-1-architecture.png`
- Create: `项目进程文件/论文初稿工作/figures/figure-4-2-modules.png`
- Create: `项目进程文件/论文初稿工作/figures/figure-4-3-order-inventory-flow.png`
- Create: `项目进程文件/论文初稿工作/figures/figure-5-1-erd.png`
- Create: `项目进程文件/论文初稿工作/figures/figure-6-*.png`

**Interfaces:**
- Consumes: component/module/table relationships from `evidence_manifest.md`.
- Produces: 300-DPI PNG figures with safe margins, Chinese labels, and no sensitive values.

- [ ] **Step 1: Implement deterministic diagrams**

  Generate the six planned analytical diagrams with fixed canvas sizes, restrained grayscale/blue styling, legible Chinese labels, and chapter-numbered filenames.

- [ ] **Step 2: Select current product screenshots**

  Copy only screenshots that match the latest UI: home, catalog, DIY editor, recommendation, scheme, order confirmation/list/detail, customer benefits, fulfillment, and AI logs. Crop browser chrome only when it does not remove product context.

- [ ] **Step 3: Run image safety and readability checks**

  Reject any image containing API keys, tokens, personal addresses, or tiny unreadable UI. Require at least 1400 pixels on the long edge for screenshots and at least 1800 pixels on the long edge for generated diagrams.

### Task 4: Write and test the thesis content model

**Files:**
- Create: `项目进程文件/论文初稿工作/thesis_content.py`
- Create: `项目进程文件/论文初稿工作/tests/test_thesis_content.py`
- Read: `项目进程文件/论文初稿工作/evidence_manifest.md`
- Read: `项目进程文件/论文初稿工作/reference_manifest.json`

**Interfaces:**
- Consumes: verified claim labels, citation keys, figure paths, and approved eight-chapter structure.
- Produces: `THESIS_META`, `ZH_ABSTRACT`, `EN_ABSTRACT`, `CHAPTERS`, `ACKNOWLEDGEMENT`, `REFERENCES`, and `APPENDICES` Python data structures.

- [ ] **Step 1: Write failing content-contract tests**

  Test that the Chinese abstract contains 300—500 Chinese characters; keywords contain 3—5 entries; chapter numbers are exactly 1—8; Chinese main-body count is at least 12000; every citation key exists; every bibliography entry is cited; required figures/tables are referenced before insertion; and forbidden completion claims are absent.

- [ ] **Step 2: Run the tests and confirm failure**

  Run:

  ```bash
  /Users/onion/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 -m unittest 项目进程文件/论文初稿工作/tests/test_thesis_content.py -v
  ```

  Expected: failure because `thesis_content.py` and the required structures do not yet exist.

- [ ] **Step 3: Write original chapter content**

  Implement the approved word allocation and section list. Each implementation section follows “business purpose → design decision → implementation process → result”; each testing result identifies its date and environment; each limitation uses future-work language.

- [ ] **Step 4: Write abstracts, acknowledgement, and appendices**

  Write matched Chinese/English abstracts, a non-person-specific acknowledgement, and an original English technical appendix with a Chinese translation of at least 800 Chinese characters. Label it as an original technical appendix, not a republished foreign paper.

- [ ] **Step 5: Run the content tests**

  Run the unittest command from Step 2. Expected: all content-contract tests pass.

- [ ] **Step 6: Run an originality-oriented prose audit**

  Search for repeated paragraphs, formulaic chapter introductions, long quotation marks, uncited bracket numbers, and adjacent paragraphs with identical sentence structure. Rewrite all detected repetitions using project-specific evidence rather than synonym replacement.

### Task 5: Build the template-based DOCX

**Files:**
- Create: `项目进程文件/论文初稿工作/build_thesis.py`
- Create: `项目进程文件/论文初稿工作/tests/test_thesis_docx.py`
- Create: `项目进程文件/基于SpringBoot的微信小程序DIY商品系统设计与实现_毕业论文初稿.docx`
- Read: `/private/tmp/weixin_shop_thesis_20260812/artifact.md`
- Read: `/private/tmp/weixin_shop_thesis_20260812/template/上海大学继教毕业设计(论文)模版-26新版.docx`

**Interfaces:**
- Consumes: all structures from `thesis_content.py`, `reference_manifest.json`, and generated figures.
- Produces: final DOCX with template-derived front matter, real heading styles, TOC field, figures, tables, citations, headers, footers, and page-number sections.

- [ ] **Step 1: Write failing DOCX structure tests**

  Test A4 geometry, exact margins, five-or-more logical sections, cover title, six `待填写` identity values, statement and pledge text preservation, absence of template instruction balloons, eight first-level chapter headings, TOC field, update-fields setting, header text, page fields, at least 15 references, required figures, and non-empty alt text.

- [ ] **Step 2: Run the DOCX tests and confirm failure**

  Run:

  ```bash
  /Users/onion/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 -m unittest 项目进程文件/论文初稿工作/tests/test_thesis_docx.py -v
  ```

  Expected: failure because the final DOCX does not yet exist.

- [ ] **Step 3: Implement template-preserving assembly**

  Copy the converted template, preserve the university identity and fixed statement/pledge text, remove instructional shapes, replace all example content, create the approved style ladder, add a real TOC field, set `w:updateFields=true`, and establish separate front-matter/body header-footer behavior.

- [ ] **Step 4: Insert prose, tables, citations, and figures**

  Add content in the approved order. Keep captions with figures, repeat table headers across page breaks, use explicit table widths, and add source-aware alt text to every image.

- [ ] **Step 5: Insert references and appendices**

  Format bibliography entries in GB/T 7714-style order using Songti/Times New Roman 10.5 pt. Start the English appendix and Chinese translation on separate pages.

- [ ] **Step 6: Run DOCX structure tests**

  Run the unittest command from Step 2. Expected: all DOCX tests pass.

### Task 6: Run structural and factual validation

**Files:**
- Create: `项目进程文件/论文初稿工作/validate_thesis.py`
- Modify: `项目进程文件/基于SpringBoot的微信小程序DIY商品系统设计与实现_毕业论文初稿.docx`

**Interfaces:**
- Consumes: final DOCX, evidence manifest, reference manifest, and artifact contract.
- Produces: a zero-exit validation report covering content and OOXML.

- [ ] **Step 1: Implement validation checks**

  Validate section geometry, styles, headings, paragraph indentation/spacing, image count and alt text, caption sequence, table sequence, citations, bibliography count, Chinese main-body count, cover placeholders, statement/pledge preservation, forbidden claims, and absence of template sample text.

- [ ] **Step 2: Run the validator**

  Run:

  ```bash
  /Users/onion/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 项目进程文件/论文初稿工作/validate_thesis.py
  ```

  Expected: exit 0 with every audit line marked `PASS`.

- [ ] **Step 3: Run packaged document audits**

  Run section, heading, image, field, footnote, content-control, accessibility, and style audits with the bundled document-skill scripts. Accept only expected template-derived warnings that are documented in the validation report.

### Task 7: Render, inspect, and revise every page

**Files:**
- Create: `项目进程文件/论文初稿工作/qa-render/page-*.png`
- Modify: `项目进程文件/基于SpringBoot的微信小程序DIY商品系统设计与实现_毕业论文初稿.docx`

**Interfaces:**
- Consumes: structurally validated DOCX.
- Produces: visually verified final DOCX and complete internal page render set.

- [ ] **Step 1: Render with the bundled renderer**

  Run:

  ```bash
  PATH=/Users/onion/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin:/Users/onion/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin/override:/Users/onion/.cache/codex-runtimes/codex-primary-runtime/dependencies/bin/fallback:$PATH /Users/onion/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 /Users/onion/.codex/plugins/cache/openai-primary-runtime/documents/26.805.11740/skills/documents/render_docx.py /Users/onion/Desktop/AI_Testing/Weixin_Shop/项目进程文件/基于SpringBoot的微信小程序DIY商品系统设计与实现_毕业论文初稿.docx --output_dir /Users/onion/Desktop/AI_Testing/Weixin_Shop/项目进程文件/论文初稿工作/qa-render --emit_pdf
  ```

- [ ] **Step 2: Inspect every page at original resolution**

  Check cover alignment, front-matter page breaks, TOC, Chinese glyphs, heading hierarchy, body rhythm, captions, image legibility, table wrapping, page numbers, headers, references, appendices, blank pages, clipping, overlap, and orphan headings.

- [ ] **Step 3: Revise and rerender until clean**

  Correct any observed defect in source content or the builder, rebuild the DOCX, rerun all tests and validators, and render to a new QA directory. The latest render must contain no known visual defect.

### Task 8: Final integrity check and delivery

**Files:**
- Verify: `项目进程文件/基于SpringBoot的微信小程序DIY商品系统设计与实现_毕业论文初稿.docx`
- Verify: `/Users/onion/Desktop/qqw/上海大学继教毕业设计(论文)模版-26新版.doc`
- Verify: `/private/tmp/weixin_shop_thesis_20260812/template/上海大学继教毕业设计(论文)模版-26新版.docx`

**Interfaces:**
- Consumes: latest passing tests, audits, validator, and page review.
- Produces: one final user-facing DOCX citation.

- [ ] **Step 1: Confirm template hashes are unchanged**

  Require original SHA-256 `0f5ced8a67611243603dc3d47befe51d6e1fef8844643bdc7bd0960f03013544` and converted-baseline SHA-256 `147858a629db49c3127f78134f6dc6abfebe522562fdc879a808d9887134f442`.

- [ ] **Step 2: Rerun all content and DOCX tests**

  Run both unittest files and `validate_thesis.py`; require zero failures and zero errors.

- [ ] **Step 3: Confirm deliverable metadata**

  Record final file size, SHA-256, rendered page count, Chinese main-body character count, reference count, English-reference count, figure count, and table count.

- [ ] **Step 4: Deliver only the final DOCX**

  Do not expose internal renders, scripts, or PDF unless requested. Cite the final DOCX exactly once with `purpose="output"` and summarize the factual limitations retained in the draft.
