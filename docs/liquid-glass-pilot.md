# Meow Budget Liquid Glass — 1.6

Scope: shared surfaces across Home, transaction history, analytics, planning/calendar, accounts, settings, search, tool grids, category selection and entry dialogs. Full rollout authorized by the user. Numeric fields, charts and financial actions retain readable semantic colors.

Pilot material uses a dense plate for readable text/amounts, independent blurred decorative light, diagonal highlights, rounded polished rims and a shallow shadow. Text is never blurred. Android 12+ supported modal windows retain actual background blur, guarded by capabilities. This is an Android visual interpretation, not Apple's native optical refraction implementation.

Dark canvas stays #000000, with neutral dark glass. Light stays ivory. Settings independently select Light / Black / System; glass and Reduce effects are device-local, persisted preferences. Reduce effects disables decorative blur/shadow, press scaling, navigation interpolation and modal blur. Glass off provides opaque surfaces. No infinite animations or graphics work runs while idle.

Home retains the exact factual balance/budget/commitment mathematics and actions. Entry emphasizes the amount, preserves type/category/account/date/fee/refund rules, and supports keyboard/scrolling. The entry panel is bottom-aligned. Navigation has a finite sliding selection plate and labels, keeps all five sections, and respects RTL. Scroll viewports reserve 80 dp below content so floating actions and transient snackbars cannot cover controls. More omits the duplicate floating action while showing its tool grid. Templates are selected as More. Touch controls retain original semantic tags and accessible labels.

Preview uses the established 1.5 development certificate. VersionCode 8, versionName 1.6.0. Actual update acceptance seeds versionCode 7, installs 8 with adb install -r and verifies financial records, saved plan, template, theme, glass and Reduce effects. Owner production signing is independent.

Verified on Android API 35 in Actions run 37 (37070339533), source commit 8b011b7241e9aaf36fa57683126f2a96cf411eb8: compilation, lint, 38 unit tests and 12 Android tests passed. The two upgrade-stage tests are skipped in the normal suite and each passed separately before/after a real adb install -r update 7 -> 8. Encrypted ledger, balance, full plan, template, theme, glass and reduced-effects preferences persisted. Light, black, entry, transactions, analytics, planning, accounts, settings and opaque fallback screenshots were reviewed; dark canvas is also pixel-checked. Launcher ANRs are isolated in the emulator, and the glass screenshot test rejects system ANR overlays.

Delivered preview APK versionCode 8 / versionName 1.6.0 / package com.hesabbeitna.app.preview. Compiled manifest confirms Meow Budget, RTL, minSdk 26, targetSdk 35, allowBackup false and no INTERNET permission. APK v2 signature verified.

APK SHA-256: 951c512fb33a57162408011894fe95808985912c96d1b71adfc3d840b39ad603.
Certificate SHA-256: 2e5fe51dd368a77be496e8ce79216c47b592bde7d819192ecae5cd2ca3a9e6cc; matches the delivered 1.5.0 preview.

No physical Samsung S25 Ultra performance measurements. The public development certificate is preview-only; owner production signing remains separate.
