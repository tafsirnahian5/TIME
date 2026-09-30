# Changelog

T!ME has not had a public release yet. This is what the development builds have gained, newest first. Schema numbers are the on-device database version; every change from 13 on migrates your data in place.

## Phase 17 — Bangla and automatic backups
- The whole app in Bangla (Settings › Language & region), with Bangla digits and month names. Your own text, stored amounts, backups and exports never change language.
- Automatic backups to a folder you choose, daily or weekly, optionally password-protected. Each new copy is verified before older ones go, and the newest 7 are kept. A failure keeps every older copy and tells you how to fix it. With automatic backups off, you get a reminder after a set number of days.

## Phase 16 — resilience & hardening
- Password-protected backups (`.timebackup`): PBKDF2 key, AES-256-GCM in authenticated chunks. A wrong password changes nothing.
- App lock: the PIN verifier uses PBKDF2 plus a key held in the Android Keystore, and older PINs upgrade on next use. PINs can be 6–12 digits. Turning the lock off needs the current PIN. Auto-lock defaults to 30 s. Widget timer buttons are refused while the lock is on.
- Database indexes for large histories (schema 37). A 10k-record performance check covers the code every screen runs.
- Release build: R8 shrinking without renaming, and signing from an untracked `keystore.properties`.

## Phase 15 — Me, settings & polish
- Me attributes as plain ratios with their formulas: Focus, Delivery, Consistency, Planning and Money review.
- New Settings pages: Timers & focus, and Language & region (clock, date order, currency label). Settings search. Wallpaper colours, pure black and reduce celebrations.
- Editor drafts on every Add form. Focus and Money home-screen widgets.
- Accessibility pass: announced selection states, headings, larger text, and full-size calendar targets.

## Phase 14 — planning & capture
- Several blocks of reserved time per task, with split and remove, and a reschedule that keeps used time (schema 36).
- Calendar filters and an Agenda view. Board sort modes, saved filters and drag-and-drop.
- Undo for deleting and editing tasks, plans and links.
- Strikethrough, quotes, inline code and editor undo/redo in notes. Checklists can become task steps.
- Choice of the day-31 rule for monthly repeats. Fixed: monthly repeats drifted to the 28th after a short month.
- Links: saved date, archive, move to folder, JSON export, and sign-in details hidden when shown (schema 35).

## Phase 13 — money
- Loans and dues, transfer fees, ledger undo and Money settings.
- Category icons and merging, a money calendar and a net-worth trend.
- Plan priority, links from money to tasks and projects, and a project time-and-money summary.
- Receipt attachments, carried in backups (schemas 31–34).

## Phase 12 — habits
- Measurable and timed habits, day notes, every-N-days and monthly targets, history charts, reorder, and a habit widget (schema 30).

## Phase 11 — notifications
- Snooze and done actions, reminder times you choose, editable quiet hours (schema 29). Timers can ring like an alarm. Budget alerts.

## Phase 10 — workday & Today
- Breaks pause work, live workday figures and an editable timeline (schema 28). Today's three commitments, paused work and money due this week. Week start and default calendar view settings.

## Phase 9 — one timer
- Focus, tasks and notifications share one work timer that survives restarts honestly (schema 27). Editable session history, presets and a break reminder.

## Phase 8 — correctness
- Finishing a task stops its timer. Task and wallet archive. Reports split time at period boundaries (schema 26). Plans can be cancelled, and the money forecast covers only this month. Missed reminders are summarised. Time zone changes are handled.

## Releases 1.1–1.7 (earlier development)
- Recurrence, blocked tasks, projects and tags, manual Board order, Week/Day views.
- Refunds, ledger filters and CSV import.
- Interruption history, dependencies, reschedule suggestions and custom Board columns.
- Links, HTML bookmarks and calendar exchange.
- Savings goals, report comparison, utility timers, the Today widget and a tablet rail.
- Monthly review, a weekly target, workload, category detail and achievement collections.
