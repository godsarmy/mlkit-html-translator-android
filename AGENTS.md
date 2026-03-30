# AGENTS

This repository uses a specialist-agent workflow for coding tasks.

## Core Workflow

1. **Understand** the request and constraints.
2. **Choose the best path** balancing quality, speed, cost, and reliability.
3. **Delegate intentionally** to specialists when it improves outcomes.
4. **Parallelize independent work** where possible.
5. **Execute changes** with minimal, focused edits.
6. **Verify** with diagnostics/build/tests relevant to the task.

## Specialist Roles

- **@explorer**: Fast discovery across unknown code areas (search, patterns, symbol mapping).
- **@librarian**: Official/current library docs and version-specific API behavior.
- **@oracle**: High-stakes architecture/trade-off analysis and complex debugging.
- **@designer**: UI/UX polish, responsiveness, visual consistency, interaction quality.
- **@fixer**: Rapid implementation for well-defined tasks; best for parallelizable execution.

## Delegation Rules

- Delegate only when expected value is higher than coordination overhead.
- If mentioning a specialist, invoke it in the **same turn**.
- Prefer direct file path references (`path/file.kt:line`) over large pasted code.
- Use multiple **@fixer** agents for 3+ independent implementation tasks.
- Avoid delegation for tiny, single-file edits or when requirements are still unclear.

## Verification Standards

- Run language diagnostics first (`lsp_diagnostics`) when applicable.
- Run project build/tests for changed scope.
- Confirm no unrelated regressions were introduced.

## Release + JitPack Publishing

Use this flow when publishing a new library version.

1. **Bump version references**
   - Update all user-facing version strings (for example `README.md` dependency snippet and any sample app `versionName`).
   - Keep versioning consistent across docs and build metadata.

2. **Verify locally**
   - Run a scoped build before release:
   - `./gradlew :library:assemble :sample:assembleDebug`

3. **Commit and tag**
   - Commit message should reflect release intent (example: `Prepare v0.1.1 release metadata`).
   - Create annotated tag:
   - `git tag -a <version> -m "Release <version>"`

4. **Push branch and tag**
   - `git push origin main`
   - `git push origin <version>`

5. **Create GitHub release**
   - `gh release create <version> --title "<version>" --generate-notes`

6. **Trigger/verify JitPack**
   - JitPack builds on demand after a tag exists.
   - Check status:
   - `https://jitpack.io/api/builds/com.github.godsarmy/mlkit-html-translator-android`
   - Artifact path format:
   - `https://jitpack.io/com/github/godsarmy/mlkit-html-translator-android/library/<version>/library-<version>.pom`
   - If status is `Building`, wait and retry. If it fails, inspect JitPack build logs from the project page and fix/re-tag as needed.

7. **Publish completion criteria**
   - GitHub release exists for the tag.
   - JitPack status for the version is successful and artifact URL resolves.

## Communication Style

- Be direct and concise.
- Ask targeted clarifying questions when requirements are ambiguous.
- Push back briefly on risky approaches and offer safer alternatives.
- Avoid unnecessary narration and flattery.
