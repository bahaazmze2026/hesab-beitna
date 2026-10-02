# Meow Budget 1.6.1 — compact navigation and dark text correction

- Removed the fixed 80 dp bottom reservation from the content viewport. Existing end-of-scroll space remains inside scrolling pages, so the add control does not require truncating all screens.
- Navigation item minimum height 66 → 48 dp (Arabic text renders around 51 dp on the API 35 emulator), icon 24 → 20 dp, label 12 → 11 sp, tighter vertical padding. Larger font settings may grow the minimum height to preserve labels.
- Floating add button 56 → 48 dp; available on Home, Transactions, Analytics and Plan. Auxiliary tool pages use their own actions.
- Theme root provides onBackground as the default content color. Shared glass surfaces provide onSurface; transparent clickable surfaces explicitly choose onSurface/onPrimaryContainer.
- Preview 1.6.1, versionCode 9, same preview identity and development signing certificate as 1.6.0.

## Verified acceptance

- Tested source: `810d08d475cb8536f409471f405d9e7248c5b4d3`.
- Actions run: https://github.com/bahaazmze2026/hesab-beitna/actions/runs/37079032310
- Compilation and lint passed; 38 unit tests passed.
- Android API 35: 14 suite methods, 0 failures, 2 expected upgrade-stage skips; 12 actual UI/persistence tests passed.
- Separate seed/verify instrumentation stages each passed one test around `adb install -r`, versionCode 8 → 9, preserving encrypted ledger, monthly plan, quick template, balance, theme, glass and reduced-effects settings.
- Runtime regression assertions bound navigation height to 48–56 dp, reject the old fixed viewport gap, and inspect rendered dark TextLayout foreground luminance at both the root and transparent clickable glass cards.
- Reviewed final light Home and dark Home/More captures, plus dark Settings and light Plan from the same unchanged application source. The large fixed bottom band is absent and texts remain legible.
- APK: `Meow-Budget-1.6.1.apk`, preview package `com.hesabbeitna.app.preview`, versionCode 9. v2 signature verified.
- APK SHA256: `8e2b0da09f2d97089ce9be42319f4223fa866b50320553b1d2eee6cccc0b277b`.
- Certificate SHA256: `2e5fe51dd368a77be496e8ce79216c47b592bde7d819192ecae5cd2ca3a9e6cc`, identical to delivered 1.6.0.
- APK and reports: https://github.com/bahaazmze2026/hesab-beitna/actions/runs/37079032310/artifacts/11257369820 (GitHub retention expires 2026-12-31).

Physical Samsung S25 Ultra performance and system font scaling were not exercised locally. The navigation uses a minimum height rather than clipping labels at larger font settings.
