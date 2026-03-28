# Session Memory

## Build/Environment notes

- Root project uses generated Gradle wrapper (`./gradlew`) and Android SDK from `local.properties`.
- `local.properties` is intentionally gitignored and required for local Android builds.
- Running Gradle tasks may generate Eclipse metadata (`.project`, `.classpath`, `.settings/`); these are ignored in `.gitignore`.

## Workflow conventions in this repo

- Work progresses by TODO phases with one commit per completed step.
- After each step: run format + tests/build, update checkboxes in `TODO.md`, then commit.
