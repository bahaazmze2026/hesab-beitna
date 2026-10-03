# Meow Budget 1.6.3 — consistent in-app Mew identity

The 1.6.2 launcher update left the separate in-app `brand_cat.png` resource intact. Version 1.6.3 replaces that shared resource byte-for-byte with the original transparent 1254×1254 `Mew-Icon-Isolated.png`. All references in `Ui.kt`, `DesignSystem.kt` and the legacy splash now resolve to the current character, including shared headers, loading/setup/lock states, empty states, analytics tips and cat category icons. The `Mascot` component no longer clips image corners. Image aspect ratio and character colors remain intact; launcher resources remain the tested 1.6.2 versions.

Notification small-icon PNGs for all five densities derive solely from original alpha and replace the old independently drawn vector. Android requires this glyph to be monochrome. Generator: `tools/generate-notification-icons.py`.

Version 1.6.3/code 11 keeps `com.hesabbeitna.app.preview` and the existing preview certificate. No financial model, storage or calculation changes.

## Verification

- Tested application source: `a1d7b284b273d843db2de804d8777bfdd3d3eb9a`; all application source and APK bytes remain identical to the first verified in-app build, with subsequent changes confined to tests and workflow diagnostics.
- Original PNG SHA256: `6c1c8390b51cf669b966dbc199a821640761c3835dbfd3c5257886b549dabcef`; `brand_cat.png` is byte-identical to this source.
- Compiled APK check verifies all 20 launcher PNGs, the full-resolution in-app RGBA pixels, and all five notification silhouettes. The independent old notification vector is removed.
- Visually reviewed light Home, dark Home, dark More, dark Plan, and filtered Transactions captures: new original character appears in shared headers and additional mascot slots, retaining all features and transparency.
- Actual update acceptance installs the delivered 1.6.2 APK (SHA256 `e705f248ff452736ea98b007cbd2ab6de7dc47b73c41d0d3c7bcbf228114e0a0`) with fixture data and applies code 11 via `adb install -r`; encrypted ledger, plan, template, balance, theme and glass settings survive.
- Certificate SHA256 remains `2e5fe51dd368a77be496e8ce79216c47b592bde7d819192ecae5cd2ca3a9e6cc`.

- Build/lint and 38 unit tests passed. In run 37110973835 the full Android suite had 15 methods, 0 failures and 3 deliberate stage skips (12 actual UI/persistence tests passed), and both actual-update stages passed. That run later failed during launcher gesture verification; a subsequent unchanged-application suite attempt encountered an existing navigation timeout. Run 37112284411 again passed the full suite and both upgrade stages before the launcher marker assertion failed.
- Initial launcher gesture delivery could trigger the wallpaper context menu; the test uses a short shell gesture from the observed hotseat. The final diagnostic captures confirmed the drawer was already open with Mew visible: its divider exposes “All apps” as an accessibility description rather than text. The test now uses that observed description.
- Final APK SHA256: `da96054cb930e1f84ba9f8c2a38cde386fbc82709d8666cb6a1d5c9424984772`; version 1.6.3/code 11, v2 signature verified against delivered 1.6.2.

- Independent native identity acceptance passed (1 test) on Android API 35: https://github.com/bahaazmze2026/hesab-beitna/actions/runs/37113093831. It resolves the installed full-resolution mascot and adaptive icon, verifies safe pixels and absence of independent shortcuts, and checks the real drawer and system Settings.
- Visually reviewed final drawer and App info screenshots. Compiled APK resource verification passed again; the final artifact APK is byte-identical to the one exercised in actual update acceptance.
- Verified APK and native screenshots: https://github.com/bahaazmze2026/hesab-beitna/actions/runs/37113093831/artifacts/11271190770 (retention expires 2027-01-01).

Physical devices, legacy Android splash rendering and timed notification delivery were not exercised; notification resources were checked inside the APK. The preview identity and owner-controlled production signing remain separate.
