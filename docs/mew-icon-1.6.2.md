# Meow Budget 1.6.2 — original Mew launcher icon

The launcher uses the supplied transparent `Mew-Icon-Isolated.png`. The original 1254×1254 RGBA file is retained byte-for-byte under `tools/icon-source/`; its SHA256 is `6c1c8390b51cf669b966dbc199a821640761c3835dbfd3c5257886b549dabcef`.

`tools/generate-launcher-icons.py` uniformly scales the complete canvas without redrawing, removing pixels, cropping or changing character colors. The adaptive foreground uses a 108 dp canvas over ivory `#FFF8EF`. Every nonzero-alpha pixel, including resampling fringe, is within the guaranteed 33 dp radius safe circle. A 30 dp source radius leaves margin for launcher motion. Circular and rounded-square masks retain the ears, tail and cash. Legacy square and round exports and alpha-derived themed-icon silhouettes cover mdpi through xxxhdpi.

| Density | Adaptive foreground | Legacy icon |
| --- | --- | --- |
| mdpi | 108 px | 48 px |
| hdpi | 162 px | 72 px |
| xhdpi | 216 px | 96 px |
| xxhdpi | 324 px | 144 px |
| xxxhdpi | 432 px | 192 px |

The previous standalone launcher foreground is removed. All adaptive and themed launcher references point to the new resources. App name, package, signing configuration, financial logic and screens are unchanged apart from the displayed version. In-app artwork and notification glyphs remain separate from launcher identity. No static or dynamic application shortcuts are implemented.

## Update identity

Preview version 1.6.2, versionCode 10, package `com.hesabbeitna.app.preview`. The preview signing certificate SHA256 remains `2e5fe51dd368a77be496e8ce79216c47b592bde7d819192ecae5cd2ca3a9e6cc`.

Acceptance installs the actual delivered 1.6.1 APK (SHA256 `8e2b0da09f2d97089ce9be42319f4223fa866b50320553b1d2eee6cccc0b277b`) and seeds data before installing 1.6.2 with `adb install -r`. It verifies encrypted ledger, full monthly plan, quick template, balance, theme and glass settings survive.

## Verification

- Tested application source: `e6a5721bf01b69788e262c950597d043666ea1bd`.
- Successful Actions run (attempt 2): https://github.com/bahaazmze2026/hesab-beitna/actions/runs/37109470283
- Compilation, lint and release build passed; 38 unit tests passed.
- Android API 35: 15 suite methods, 0 failures, 3 deliberate stage skips; 12 actual UI/persistence tests passed. Separate seed/verify upgrade stages each passed one test. The independently invoked native launcher test passed one test.
- Runtime checks resolve `AdaptiveIconDrawable`, confirm every rendered foreground pixel stays in the safe circle, and verify no static or dynamic shortcut icons exist.
- Visually reviewed `qa-mew-app-drawer.png` and `qa-mew-app-info.png` from Pixel Launcher and system Settings: original colored character, ears, cash and tail remain visible against ivory.
- `tools/check-launcher-apk.py` verified all 20 compiled density/family PNGs pixel-for-pixel against exports; the obsolete standalone foreground is absent.
- Final signed preview APK SHA256: `e705f248ff452736ea98b007cbd2ab6de7dc47b73c41d0d3c7bcbf228114e0a0`; versionCode 10. APK Signature Scheme v2 verified, with certificate identical to delivered 1.6.1.
- Final APK and reports: https://github.com/bahaazmze2026/hesab-beitna/actions/runs/37109470283/artifacts/11269576548 (retention expires 2027-01-01).

An earlier launcher invocation failed to select Home reliably; the test now explicitly selects the launcher. A subsequent full-suite attempt hit a transient existing expense-validation assertion; rerunning unchanged source passed. These failed attempts are not counted as successful verification.

Physical Samsung or other vendor launchers have not been exercised. The distributed APK is the existing signed preview identity; production signing remains owner-controlled.
