# Maintainers: How this mirrors `mlkit-markdown-translator-android`

This project intentionally aligns with markdown-translator ergonomics.

## API shape parity

- main class + default/options constructors
- callback-based `translate*` call
- typed exception + error code enum
- explicit `close()` lifecycle hook
- optional timing listener/report

## Architecture parity

- traversal/eligibility stage
- token protection stage
- chunking + marker mapping stage
- translation orchestration + fallback policy stage

## Boundary parity

- library remains app-agnostic
- model lifecycle remains outside library API
- sample app demonstrates app-side model ownership

## Operational parity

- Spotless formatting
- unit tests + CI workflow
- benchmark/report docs for tuned defaults
