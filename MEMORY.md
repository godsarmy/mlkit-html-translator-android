# Session Memory

## Build/Environment notes

- Root project uses generated Gradle wrapper (`./gradlew`) and Android SDK from `local.properties`.
- `local.properties` is intentionally gitignored and required for local Android builds.
- Running Gradle tasks may generate Eclipse metadata (`.project`, `.classpath`, `.settings/`); these are ignored in `.gitignore`.

## Workflow conventions in this repo

- Keep edits focused and release-oriented.
- Validate changed scope with format + tests/build before publishing.

## Implementation learnings

- Chunk-size tests must account for segment-marker overhead; tiny `maxChunkChars` can split more than expected.
- Resilience path should treat marker parse mismatch separately from normal translation failure:
  retry with smaller chunks first, then fallback per-node for the failing chunk.
- Unit tests in different packages cannot call package-private constructors; keep cross-package tests on public APIs or same-package tests.
- `LiveData` unit tests require `InstantTaskExecutorRule`; using `postValue` keeps ViewModel updates safe across test/runtime threads.
- ML translation can mutate structural marker text; avoid relying on verbose natural-language marker tokens for chunk reassembly.
- For reliability, use marker-free translation for single-node chunks and sanitize orphan marker fragments before final DOM write-back.

## Product decision: translation result caching is app-owned, not library-owned

- `MlKitHtmlTranslator` should execute translation per request without internal result caching.
- If caching is desired, implement it in app/repository logic so each app controls lifetime and eviction policy.
