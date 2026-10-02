# Preview development certificate

`preview.keystore` is intentionally PUBLIC development signing material, with the standard Android debug password `android` and alias `androiddebugkey`. It is ONLY for the debuggable `com.hesabbeitna.app.preview` build. Anyone can reproduce a preview signed by this key: use these APKs only from this project's trusted delivery. Never use this key for production or distribute a production release signed by it.

The explicit checked-in development key makes future preview signatures reproducible and independent of runner caches. Production release signing still uses the owner's separate `signing.properties` / encrypted secrets. Keep the application ID and device vault alias stable during the visible rename.

The 1.4 preview has a different certificate and cannot update to 1.5 in place. Export a password-encrypted backup BEFORE removing that old preview; install 1.5 and restore. This is a one-time preview migration. Schema 2 imports schema 1 backups; older apps cannot read the new schema 2 backups.

CI verifies a real upgrade: install versionCode 6, seed ledger + full plan + template + local theme, install versionCode 7 with `adb install -r`, then verify the encrypted records, balance, theme and glass setting persist. Release keys are never involved.
