# Markdown 1.3.0 alignment verification

Reference: `mlkit-markdown-translator-android`, tag `1.3.0`, commit
`8eb488c559f484d60b4f1a6198a6b8d94dc2359e`.

## Automated checks

The following checks passed locally during the upgrade:

```bash
./gradlew spotlessApply
./gradlew :library:testDebugUnitTest :sample:testDebugUnitTest
./gradlew spotlessCheck :library:lintDebug :sample:lintDebug \
    :library:assemble :sample:assembleDebug :sample:assembleRelease
```

- 61 library tests and one sample test passed, with no failures or skips.
- Library lint reported no issues. Sample lint reported 78 warnings and no errors.
- Both library variants, the sample debug APK, and the sample release APK built.
- The release build completed R8 optimization and resource shrinking without additional
  jsoup keep rules or missing-class suppressions.
- CI now checks both modules' unit tests and builds the optimized release APK as well.

The library tests cover HTML structure and protected elements, masking, marker-free
single-node translation, orphan marker cleanup, RTL metadata, and lifecycle behavior.
A new repeated-request test verifies that the same translator calls its backend again
and returns fresh results for identical requests; pooling ML Kit clients is separate
from caching translation results.

## Device smoke check

On a connected Android 15 (API 35) device:

- The debug APK installed and launched.
- A screenshot confirmed dark-theme controls sit below the status bar and above the
  gesture navigation area after adding the drawer's outer inset container.
- A temporary copy of the optimized release APK was signed with the local debug key,
  installed, and launched successfully. No signing configuration or key was added to
  the repository.

Further device interaction was limited by the system notification/lock overlay.
Keyboard, light theme, three-button navigation, all secondary screens, and actual
cross-language ML Kit output remain manual validation items. API 36 runtime behavior
has not been exercised on a device; compilation and lint use SDK 36.
