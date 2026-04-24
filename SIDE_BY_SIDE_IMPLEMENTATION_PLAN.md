# Side-by-Side Compare Implementation Plan

1. **Port side-by-side implementation scaffold from markdown sample**
   - Reference:
     - `/home/godsarmy/github/mlkit-markdown-translator-android/sample/src/main/java/io/github/godsarmy/mlmarkdown/sample/SideBySideCompareActivity.java`
     - `/home/godsarmy/github/mlkit-markdown-translator-android/sample/src/main/res/layout/activity_side_by_side_compare.xml`
   - Create equivalents in html sample:
     - `sample/src/main/java/io/github/godsarmy/mlhtmltranslator/sample/SideBySideCompareActivity.java`
     - `sample/src/main/res/layout/activity_side_by_side_compare.xml`

2. **Add side-by-side icon to main toolbar action row**
   - File: `sample/src/main/res/layout/activity_main.xml`
   - Insert `compareModeButton` **immediately left of** `saveTranslatedButton`
   - Match markdown sizing/spacing/tint style (40dp, 6dp padding, 8dp start margin before save icon group).

3. **Wire launch from MainActivity**
   - File: `sample/src/main/java/io/github/godsarmy/mlhtmltranslator/sample/MainActivity.java`
   - Add:
     - `findViewById` for compare button
     - click listener -> `openSideBySideCompare()`
     - intent extras for original HTML + translated HTML
   - Keep save/share enable-state logic consistent with compare availability (only active when translated content exists).

4. **Implement compare screen behavior**
   - File: `SideBySideCompareActivity.java`
   - Features:
     - left pane = original HTML, right pane = translated HTML
     - render toggle: raw text view ↔ rendered WebView
     - top-right close icon -> `finish()`
     - scroll sync between panes (raw mode and rendered mode)
     - theme-aware HTML wrapper CSS for WebView render path

5. **Landscape-only enforcement**
   - File: `sample/src/main/AndroidManifest.xml`
   - Register `SideBySideCompareActivity` with:
     - `android:screenOrientation="landscape"`
   - Do not change orientation policy of existing activities.

6. **Top-right hidable controls in compare screen**
   - Files:
     - `activity_side_by_side_compare.xml` (icon container top|end)
     - `SideBySideCompareActivity.java` (visibility/timer logic)
   - Behavior:
     - initial visible
     - auto-hide after timeout
     - fade in/out animation
     - show again on touch interaction

7. **Resource parity (icons/strings/colors)**
   - Files:
     - `sample/src/main/res/values/strings.xml`
     - drawable icons if missing (compare / render / close)
   - Add/align content descriptions and mode-specific labels.
   - Reuse existing color tokens (`mlkit_on_surface_variant`, `mlkit_primary`, etc.) for style parity.

8. **Verification**
   - Build: `./gradlew :sample:assembleDebug`
   - Install: `adb install -r sample/build/outputs/apk/debug/sample-debug.apk`
   - Manual checks:
     - compare icon is left of save
     - side-by-side screen is landscape only
     - top-right render + quit icons auto-hide and reappear on touch
     - render toggle works and updates icon/tint/description
     - visual spacing/sizing matches markdown sample style

9. **Optional hardening pass**
   - Add null/empty guards for missing intent extras.
   - Ensure WebView lifecycle cleanup in `onDestroy()`.
   - Confirm no regressions to existing save/share/render flow in main screen.
