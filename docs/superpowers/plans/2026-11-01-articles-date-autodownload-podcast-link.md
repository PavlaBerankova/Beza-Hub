# Article date filter, auto-download on queue, podcast custom link — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline, in this session) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three features to BEZA Hub: a date filter on Články, automatic download when an episode is added to the queue (with a Settings switch), and a user-defined named link per podcast.

**Architecture:** (1) Reuse the existing `NewPeriod` / `filterByPeriod` logic (left over from the removed Nové tab) in `ArticlesViewModel`, cycled by a `DotFilterButton` like the podcast page. (2) `QueueRepository` gets an `onAdded` hook that `AppContainer` wires to `DownloadRepository.download` when the new `DownloadSettings.autoDownloadOnQueue` flag is on, so every add-to-queue path is covered once. (3) `PodcastFeedEntity` gets nullable `linkTitle` / `linkUrl` columns (Room migration 14→15), edited via a dialog and opened from the podcast page's ⋯ menu.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room, SharedPreferences, JUnit4 + kotlinx-coroutines-test.

**Spec:** Agreed in conversation on 2026-11-01 (no separate document); the three feature descriptions below under "Global Constraints" are its verbatim decisions.

## Global Constraints

- Work on a branch `feature/articles-date-autodownload-link` off `main`; one commit per task.
- UI copy is Czech, in `app/src/main/res/values/strings.xml` (no hard-coded strings in new composables).
- Articles date filter: cycle **Všechny → Dnes → Týden → Měsíc → Všechny** (no "14 dní"); "Dnes" = since local midnight, Týden = last 7 days, Měsíc = last 30 days; combines with the tag filter; articles without a date drop out while a period is active.
- Auto-download: Settings → Stahování switch, **default ON**; the existing Wi-Fi-only setting still applies; triggers **only** on explicit download or on add-to-queue — **not** when an episode is merely played (`queueAtFront`), not for YouTube videos, not retroactively for episodes already in the queue.
- Custom link: one per podcast, fields **název** (optional; falls back to the domain without `www.`) and **adresa** (required, http/https; a bare `www.…` gets `https://` prepended); lives in the podcast page's ⋯ menu; once set the menu shows the link's name plus *Upravit odkaz* and *Odebrat odkaz*; opens in the browser.
- Room schema is exported: bump `AppDatabase` to version 15 and commit the generated `app/schemas/.../15.json`.

## Review Focus

- A link like `javascript:alert(1)`, `ftp://x`, `https://` (no host) or `foo bar` is rejected, not saved (Task 3 test).
- Empty/blank link title shows the domain (`https://www.forendors.cz/x` → `forendors.cz`) (Task 3 test).
- Articles with `pubDateMillis == null` disappear under an active period and return under *Všechny* (Task 1 test).
- A failing auto-download (hook throws) must not make adding to the queue fail (Task 2 test).
- Adding an episode that is already queued must not trigger a second download (Task 2 test).
- Existing podcasts survive the 14→15 migration with no link (Task 3: manual run on a device/emulator with existing data).

---

### Task 1: Date filter on Články

**Files:**
- Create: `app/src/main/java/com/berpod/app/ui/articles/ArticlePeriod.kt`
- Modify: `app/src/main/java/com/berpod/app/ui/articles/ArticlesViewModel.kt` (state + pipeline), `ArticlesScreen.kt` (button row + empty state), `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/berpod/app/ui/articles/ArticlePeriodTest.kt`

**Interfaces:**
- Consumes: `NewPeriod`, `filterByPeriod(items, period, nowMillis, zone, publishedAt)` from `ui/newepisodes/NewPeriod.kt`; `DotFilterButton(label, highlighted, onClick, modifier, flipped)`.
- Produces: `fun nextArticlePeriod(period: NewPeriod): NewPeriod`; `fun NewPeriod.articleTitleRes(): Int`; `ArticlesUiState.period: NewPeriod = NewPeriod.All`; `ArticlesViewModel.onCyclePeriod()`.

- [ ] **Step 1: Write failing tests** in `ArticlePeriodTest`:
  - `cycle skips two weeks`: `nextArticlePeriod` applied from `All` four times yields `Today, Week, Month, All`.
  - `period filter drops undated articles`: with `filterByPeriod(listOf(1L to null, 2L to now - 1000), NewPeriod.Week, now) { it.second }` → only item 2; with `NewPeriod.All` → both.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*ArticlePeriodTest*'` — expect FAIL (`nextArticlePeriod` unresolved).
- [ ] **Step 3: Implement `nextArticlePeriod`** in `ArticlePeriod.kt` over the list `[All, Today, Week, Month]`; add `NewPeriod.articleTitleRes()` mapping to new strings `articles_period_all` "Všechny články", `articles_period_today` "Dnešní články", `articles_period_week` "Za posledních 7 dní", `articles_period_month` "Za poslední měsíc" (`TwoWeeks` maps to the week string; unreachable).
- [ ] **Step 4: Wire the view model.** Add `private val period = MutableStateFlow(NewPeriod.All)`, `period` in `ArticlesUiState`, include it in the existing `combine(...)` (third flow) and apply `filterByPeriod(…, period, System.currentTimeMillis()) { it.article.pubDateMillis }` after the tag filter; `onCyclePeriod()` sets `period` to `nextArticlePeriod(period.value)` and mirrors it in `_uiState`.
- [ ] **Step 5: Wire the screen.** In `ArticlesScreen`, below the tag chips and above the list (only when `hasAnySource`), add a row like `PeriodRow` in `ui/newepisodes/NewEpisodesScreen.kt` (left `DotFilterButton(stringResource(period.articleTitleRes()), highlighted = period != NewPeriod.All, …, Modifier.offset(x = (-10).dp))`, then `HorizontalDivider`; no sort button). Add an empty-state branch before the tag-empty one: when both lists are empty and `period != NewPeriod.All` show `articles_period_empty` "V tomto období nejsou žádné články" / hint "Zvolte delší období".
- [ ] **Step 6: Run** `./gradlew :app:testDebugUnitTest :app:assembleDebug` — expect PASS / BUILD SUCCESSFUL.
- [ ] **Step 7: Commit** `feat: date filter (dnes/týden/měsíc) on Články`.

### Task 2: Auto-download when adding to the queue

**Files:**
- Modify: `data/repository/QueueRepository.kt`, `data/repository/DownloadSettings.kt`, `di/AppContainer.kt`, `ui/settings/SettingsScreen.kt`, `res/values/strings.xml`
- Test: `app/src/test/java/com/berpod/app/data/repository/QueueRepositoryTest.kt` (extend; reuse its `RecordingQueueDao`)

**Interfaces:**
- Consumes: `DownloadRepository.download(episodeId: Long)`; `SettingsSwitchRow(title, hint, checked, onCheckedChange)`.
- Produces: `QueueRepository(queueDao: QueueDao, onAdded: suspend (episodeId: Long) -> Unit = {})`; `DownloadSettings.autoDownloadOnQueue: StateFlow<Boolean>` and `setAutoDownloadOnQueue(value: Boolean)`.

- [ ] **Step 1: Write failing tests** in `QueueRepositoryTest` (build the repo with a recording hook list):
  - `addToQueue calls the hook once with the episode id`.
  - `addToQueue of an already queued episode fails and does not call the hook`.
  - `a throwing hook does not fail addToQueue` (hook throws `IllegalStateException`; result `isSuccess`, episode is queued).
  - `queueAtFront does not call the hook`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*QueueRepositoryTest*'` — expect FAIL (constructor has no `onAdded`).
- [ ] **Step 3: Implement the hook.** In `addToQueue`, after `queueDao.insert(...)`, call `runCatching { onAdded(episodeId) }` (swallow; do not swallow `CancellationException` semantics beyond what `runCatching` already does elsewhere in the file).
- [ ] **Step 4: Implement the setting.** `DownloadSettings`: key `downloads_auto_on_queue`, default `true`, same `MutableStateFlow` + setter pattern as `wifiOnly`.
- [ ] **Step 5: Wire it.** `AppContainer.queueRepository`: `QueueRepository(database.queueDao(), onAdded = { id -> if (downloadSettings.autoDownloadOnQueue.value) downloadRepository.download(id) })`.
- [ ] **Step 6: Settings UI.** In the Stahování group of `SettingsScreen`, add a `SettingsSwitchRow` above the Wi-Fi row: title `settings_downloads_auto` "Stahovat po přidání do fronty", hint `settings_downloads_auto_hint` "Epizoda se po přidání do fronty sama stáhne (podle volby Wi-Fi níže).".
- [ ] **Step 7: Run** `./gradlew :app:testDebugUnitTest :app:assembleDebug` — expect PASS.
- [ ] **Step 8: Commit** `feat: download episodes automatically when queued (setting)`.

### Task 3: Custom link per podcast

**Files:**
- Create: `ui/podcastlist/PodcastLink.kt` (pure helpers), `ui/podcastlist/EditLinkDialog.kt`
- Modify: `data/local/PodcastFeedEntity.kt`, `data/local/AppDatabase.kt` (version 15), `data/local/Migrations.kt`, `di/AppContainer.kt` (register `MIGRATION_14_15`), `data/local/PodcastFeedDao.kt`, `data/repository/PodcastRepository.kt`, `ui/podcastlist/PodcastEpisodesViewModel.kt`, `PodcastEpisodesScreen.kt`, `PodcastHeader.kt` (`PodcastMenuActions`, `PodcastMoreMenu`), `res/values/strings.xml`
- Test: `app/src/test/java/com/berpod/app/ui/podcastlist/PodcastLinkTest.kt`
- Generated: `app/schemas/com.berpod.app.data.local.AppDatabase/15.json`

**Interfaces:**
- Produces: `fun normalizeLinkUrl(raw: String): String?`; `fun linkLabel(title: String?, url: String): String`; `PodcastFeedEntity.linkTitle: String? = null`, `linkUrl: String? = null`; `PodcastFeedDao.updateLink(feedId: Long, title: String?, url: String?)`; `PodcastRepository.setLink(feedId: Long, title: String?, url: String?)` (null url clears both); `PodcastEpisodesUiState.feedLinkTitle/feedLinkUrl: String?`, `isEditingLink: Boolean`, `linkError: String?`; VM `onEditLink()`, `onEditLinkDismissed()`, `onSaveLink(title: String, rawUrl: String)`, `onRemoveLink()`; `PodcastMenuActions` gains `linkLabel: String?`, `onOpenLink: () -> Unit`, `onEditLink: () -> Unit`, `onRemoveLink: () -> Unit`.

- [ ] **Step 1: Write failing tests** in `PodcastLinkTest`:
  - `normalizeLinkUrl`: `"https://forendors.cz/x"` unchanged; `" www.forendors.cz "` → `"https://www.forendors.cz"`; `"http://a.cz"` unchanged; `null` for `""`, `"javascript:alert(1)"`, `"ftp://x.cz"`, `"https://"`, `"foo bar"`, `"nodots"`.
  - `linkLabel("Forendors – předplatné", url)` → that title; `linkLabel(null, "https://www.forendors.cz/x")` → `"forendors.cz"`; `linkLabel("  ", …)` → `"forendors.cz"`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*PodcastLinkTest*'` — expect FAIL.
- [ ] **Step 3: Implement the helpers** in `PodcastLink.kt` using `java.net.URI` (host must be non-empty and contain a dot; scheme http/https only; no whitespace). Run the test — expect PASS.
- [ ] **Step 4: Data layer.** Add the two nullable columns to `PodcastFeedEntity`; `MIGRATION_14_15` runs `ALTER TABLE \`podcast_feeds\` ADD COLUMN \`linkTitle\` TEXT` and the same for `linkUrl`; bump `AppDatabase.version` to 15; add the migration to `AppContainer`'s `addMigrations`; add `updateLink` query and `PodcastRepository.setLink`. Run `./gradlew :app:assembleDebug` and confirm `15.json` is generated.
- [ ] **Step 5: View model.** Map the feed's `linkTitle`/`linkUrl` into state where `observeFeed(feedId)` is collected; `onSaveLink` runs `normalizeLinkUrl`, on `null` sets `linkError` ("Zadejte platnou webovou adresu"), otherwise `setLink(feedId, title.trim().ifBlank { null }, url)` and closes the dialog with snackbar "Odkaz uložen"; `onRemoveLink` clears and shows "Odkaz odebrán".
- [ ] **Step 6: Menu + dialog.** `EditLinkDialog(initialTitle: String, initialUrl: String, errorMessage: String?, onSave: (title: String, url: String) -> Unit, onDismiss: () -> Unit)` modelled on `EditRssDialog` with two `OutlinedTextField`s (labels "Název odkazu", "Adresa"; title hint "např. Forendors – předplatné"). In `PodcastMoreMenu`, when `linkLabel == null` show *Přidat vlastní odkaz* (→ `onEditLink`); otherwise show an item with the label (→ `onOpenLink`), *Upravit odkaz* and *Odebrat odkaz*. In `PodcastEpisodesScreen`, `onOpenLink` calls `LocalUriHandler.current.openUri(uiState.feedLinkUrl)` (pattern from `ArticleReaderScreen.kt:63`) and the dialog is shown while `uiState.isEditingLink`.
- [ ] **Step 7: Run** `./gradlew :app:testDebugUnitTest :app:assembleDebug` — expect PASS.
- [ ] **Step 8: Manual check** on a device/emulator that already has podcasts: app starts without a migration crash, existing podcasts show *Přidat vlastní odkaz*; add a link with a name, reopen the menu, tap the name → browser opens; edit; remove.
- [ ] **Step 9: Commit** `feat: custom named link per podcast`.

### Task 4: Docs and final verification

**Files:** Modify `README.md` (Články: date filter; Podcasty: custom link in the ⋯ menu; Stahování: auto-download switch).

- [ ] **Step 1: Update the three README bullets** in the existing Czech style.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest :app:assembleDebug` — expect all tests PASS, BUILD SUCCESSFUL.
- [ ] **Step 3: Manual smoke test:** Články filter cycles and combines with a tag; with the switch ON adding an episode to the queue starts a download (and with it OFF it does not); playing an episode never starts one.
- [ ] **Step 4: Commit** `README: date filter, auto-download, podcast link`.
