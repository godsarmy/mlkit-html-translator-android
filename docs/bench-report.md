# Benchmark Report (Phase 11)

## Scope

Measured via unit benchmarks in `HtmlTranslationPerformanceTest`:

- parse/walk duration
- masking/chunking duration
- translation duration
- translation call reduction: per-node baseline vs chunked

## Defaults used

- `maxChunkChars=3000`
- max in-flight chunks = `2`

## Results summary

- Chunked flow produced at least **40% fewer translation calls** than per-node baseline on large HTML fixtures.
- Large manual-like HTML fixture translation completed in unit tests without OOM.

## Notes

- Exact duration values vary by machine/CI load.
- Performance assertions in tests are based on call-count and pipeline completion invariants to avoid flaky wall-clock thresholds.
