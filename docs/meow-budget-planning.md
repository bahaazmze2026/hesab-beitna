# Meow Budget 1.5 — implementation and acceptance

Visible name: Meow Budget. Existing package IDs, encrypted vault alias, Room database and backup crypto framing remain stable. Cat-wallet asset reused in header, setup, lock, loading and empty states; quiet Meow companion messages in home, planning, calendar, templates and successful transaction / plan saves. Light, independent Black Dark and glass controls retained.

## Saved plans

Dedicated Planning navigation: monthly plan / calendar / progress. One plan per salary cycle, editable with an explicit review dialog. Stored expected income, income date, variable spending, commitments snapshot, saving target, reserve and note. Save replaces only that cycle's plan; no ledger, balance, budget or goal account mutation. A funding gap is disclosed both before save and on the saved plan. Upcoming cycles can be selected up to 12 months ahead.

Known commitments at planning time = full due amounts within selected cycle + outstanding earlier dues. Full amount prevents a bill payment from shrinking the saved spending target. Changes to dues do not silently alter the saved snapshot: UI displays the current known amount and a review prompt. Progress uses actual cycle income and net spending, separating due-linked from non-due expenses; refunds reduce the appropriate component and transfers do not enter either. Savings progress uses recorded surplus and explicitly says it does not prove an actual transfer to savings.

## Calendar and templates

Calendar is Gregorian with Saturday-first weekdays, padded day grid, event dots, month browsing, Today shortcut and selected-day details. Shows persisted expected income separately as planned only, plus due status/remaining balance. Due actions open the existing amount/account review form, saving updates actual expenses and balances. Paid/future dues remain available for review. Future transactions remain disallowed.

Templates support reviewed income/expense drafts, editable title/amount/category/account/note, deletion without deleting prior transactions, and saving a template together with a transaction atomically. A template never runs automatically. Draft use generates a fresh transaction ID/date. Archived account/category disables use until edited. At most 100 templates, at most 2400 cycle plans.

## Data and signing

Schema 2 adds plans/templates to encrypted persistence and password-encrypted backups. New builds accept validated schema 1 and 2 files; schema 1 upgrades on load/import. Unsupported schema and corrupt files fail before replacement. Old applications cannot read new schema 2 backups.

Development-only PUBLIC signing key retained in `tools/preview-signing/preview.keystore`. Explicit debug configuration only, `.preview` application ID, standard debug password. Production continues to require owner signing material. Do not use a preview key for production. 1.4 -> 1.5 preview requires encrypted backup migration once because 1.4 used a different certificate.

CI checks are designed to run JVM planning cases, financial/backup checks, lint, Android persistence/UI/crypto round-trip, and an actual `adb install -r` 6 -> 7 update preserving records/theme. Results are pending until the workflow finishes; no pass is claimed here yet.
