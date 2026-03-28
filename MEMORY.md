# Session Memory

## Build/Environment notes

- Root project uses generated Gradle wrapper (`./gradlew`) and Android SDK from `local.properties`.
- `local.properties` is intentionally gitignored and required for local Android builds.
- Running Gradle tasks may generate Eclipse metadata (`.project`, `.classpath`, `.settings/`); these are ignored in `.gitignore`.

## Workflow conventions in this repo

- Work progresses by TODO phases with one commit per completed step.
- After each step: run format + tests/build, update checkboxes in `TODO.md`, then commit.

## Implementation learnings

- Chunk-size tests must account for segment-marker overhead; tiny `maxChunkChars` can split more than expected.
- Resilience path should treat marker parse mismatch separately from normal translation failure:
  retry with smaller chunks first, then fallback per-node for the failing chunk.
