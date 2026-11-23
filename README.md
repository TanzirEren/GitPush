<div align="center">

<img src="app/src/main/res/drawable-nodpi/app_icon_full.png" width="110" alt="GitPush icon" />

# GitPush

**A premium black-and-white GitHub toolkit for your phone: push, edit code, run Actions, publish releases.**

`com.gitpush.app` · v2.2.0 · Android 7.0+ · Java + XML

</div>

---

## What's new in v2.2.0 — Import Studio
Import is now a full GitHub-style studio: look up any repo, browse it, tick exactly what to copy (nothing ticked = everything), then import in the background with notifications.
1. **Smart URL parser** – `owner/repo`, https, `.git`, SSH, `/tree/branch`, `/releases/tag/v1` links; **Paste** button.
2. **Repo X-ray** – description, homepage, topics, license, language, stars/forks, dates, size, counts of files/releases/workflows/issues/labels.
3. **File tree with tri-state checkboxes** (folders expand, partial selection shown), search, size per folder.
4. **Selection tools** – All / None / Invert / glob pattern / *Source only* / *Skip junk* / *Docs* / Expand all.
5. **Branch, tag or commit SHA picker** – import any ref.
6. **Releases picker** – choose releases, copy notes, draft/pre-release flags and **assets**; created oldest-first, newest becomes Latest.
7. **Actions tab** – pick workflow files, recent runs; workflows are pushed **last** in their own commit with `[skip ci]` (optional) so the import and releases never trigger them; optional auto-disable.
8. **Secrets & variables** – variables are copied; secrets (GitHub never reveals values) are listed from the workflows and you type the ones you want – encrypted on the device and written to the new repo.
9. **Commit history replay** – last 10/25/50/100 commits recreated with messages, original authors and dates (first-parent line) or a fresh single commit.
10. **Issues, labels & milestones** – open or all, optional comments, closed state kept, @mentions neutralised so nobody is pinged, paced for GitHub abuse limits.
11. **Pre-import dialog** – name (availability checked), description, homepage, topics, public/private, optional "imported from" note.
12. **Import into an existing repo / folder** – with replace-or-skip conflict handling.
13. **Dry-run review** – a summary of exactly what will be created before anything happens.
14. **Background engine** – foreground service, progress notification, finish notification, cancel, resumable steps.
15. **Jobs screen** – live log (copy), per-step status, **Retry failed steps**, import history.
16. **Executable bit kept** – `gradlew` and other 100755 files stay executable (new in the push engine too).
17. **Batch import (loop)** – paste many repos, they import one after another.
18. **Sync Loop (loop)** – keep any copy updated automatically: every 15 min – 24 h only files whose git hash changed are copied in ONE commit, optional mirror-deletes, pause/resume, "Sync now", link any two repos manually.

19. **Keyboard and dialog fixes** – tall dialogs scroll with the buttons always visible, IME Next/Done/Go/Search keys, URL keyboard, no autocorrect on names and paths.

Limits (GitHub itself): secret values cannot be read, Actions run history and pull requests cannot be copied, issue dates/authors become a note, tags point at the import commit (replayed history cannot reuse original SHAs), submodule contents are not copied, files over 50 MB are skipped.

## What was new in v2.1.0
- **Release tags are editable** – rename a tag, tag a specific commit, force-move it to a branch head, delete the old tag, mark "Latest".
- **Release Order Studio** – put any release first, last or anywhere between (move, swap, "move to position", reverse, sort by version/title/date). Applying gives each tag a date-anchor commit (same files, new date) because GitHub orders releases by tag-commit date. One-tap **undo**.
- **New launcher icon** (the GP mark, adaptive + round + monochrome-ready).
- **Code IDE** – preview icon (Markdown / HTML) in the toolbar left of Save; **bulk select** in the explorer (all / none / invert / by pattern) → download (ZIP or single file), delete, move, copy to, copy paths.
- **Downloads fixed** – files are created with their real MIME type and clashes become `name (1).zip`, so ZIP/APK files always unzip. The folder is remembered (Profile → Download folder).
- **Job logs** – GitHub-style select & copy mode, copy line / range / errors only / whole log.
- **APK signing tool** – generate RSA/EC keystores on the phone (.p12, .jks, .bks), fingerprints (SHA-1/256, MD5, Facebook hash), key vault, import/convert, push `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` to several repos at once, ready-to-copy workflow/Gradle snippets, full ZIP with `key.txt`, inspect any APK's signing certificate.
- **Social preview studio** – generates or fits a 1280×640 image (<1 MB), saves/commits it and opens the exact GitHub settings page (GitHub has no upload API).
- **Control center (18 new GitHub controls)** – Issues, Pull requests, Branches & tags (+ protection), Collaborators & invitations, Webhooks (+ deliveries), Deploy keys, Labels & milestones, Insights (traffic, clones, languages), Repo settings & security, GitHub Pages, Actions cleaner (caches/runs), Environments (+ environment secrets/variables), Security alerts, Gists, Notifications, Starred repos, Bulk repo actions, Search, Profile editor.

## What was new in v2.0.0
- **Complete redesign** – premium monochrome SaaS design system (soft cards, black buttons, Manrope + Inter + Space Mono, one Lucide-style vector icon family, no emoji icons).
- **Code IDE** – glass file explorer, file tabs, pinch-zoom editor, undo / redo, syntax highlight, red error marks, Markdown & HTML preview, one-commit saving.
- **ZIP extractor v2** – tree & list views, folder selection, filters, path find & replace, presets, previews and more.
- **Actions control** – live timers, build-time chart, run details with step timeline, job logs, artifacts, notifications.
- **Secrets & variables manager** with on-device encryption, **Import repository**, background uploads, upload speed & ETA.

## Features

### Design system
Strict monochrome palette (`#080808`, `#171717`, `#333333`, `#777777`, `#E8E8E8`, `#F5F5F5`, `#FFFFFF`), 20 px cards with soft shadows, black primary / white secondary buttons, light-gray inputs, grayscale charts, 4-8-12-16-24-32 spacing, adaptive width on tablets, light / dark / system theme.

### Code IDE
- Glass **explorer drawer** with tree structure, per-extension badges (`JAVA`, `XML`, `MD` …), search, create / import icons
- Long-press **file**: open, rename, move / change path, duplicate, export, copy path, discard changes, delete
- Long-press **folder**: new file / folder here, import here, rename, move, export as ZIP, copy path, delete
- **Tabs** (one per file, one line), undo / redo, save, commit, ⋮ menu → settings page
- Editor: 8-28 sp font, **two-finger pinch zoom**, fixed line numbers, viewport-only highlighting (smooth on big files), auto-indent, symbols bar
- **Red error marks** for JSON, XML and unbalanced brackets / strings
- Find & replace (regex, match case), go to line, trim whitespace
- **Markdown preview** and **HTML preview** (WebView, JS off by default), image preview
- All edits, renames, moves, deletes and imports are sent as **one atomic commit**

### Upload & ZIP
- Files, folders (structure kept), ZIP, share-from-other-apps, upload ZIP / APK **as-is**
- **ZIP extractor v2**: choose the inner root folder, strip levels, add prefix, tree / list view, folder-level selection, sort, extension chips, size filters, include / exclude globs, path find & replace, lowercase / uppercase / space cleanup, duplicate & long-path warnings, file preview, ZIP info, select by pattern, undo selection, presets, **Add & push** shortcut
- Parallel blob upload, live **speed + ETA**, cancel, retry failed, replace / skip dialog, unchanged files skipped
- **Foreground-service** progress notification so uploads continue in the background

### GitHub Actions
- Runs with filters (status / branch), live elapsed timers, auto-refresh, build-time chart, reload & restart icons
- Run details: step progress, per-job timeline with step durations, **job log viewer** (search, errors emphasised, jump to end)
- Re-run all / failed jobs, cancel, delete, **download artifacts and logs**, run workflows with inputs, enable / disable workflows
- **Notify me when a run finishes** (background watcher)

### Secrets, variables, releases, repos
- **Secrets**: encrypted on-device with libsodium-compatible sealed boxes, update / delete, `.env` bulk import (file or paste), random generator, search
- **Variables**: create / edit / delete
- **Releases**: create, edit, publish drafts, template / GitHub auto / AI notes, upload assets with progress
- **Import repository**: fork, or copy any accessible repo into a fresh repo with one commit
- Rename, edit info, topics, default branch, archive, download ZIP, delete (type-to-confirm), favorites

### AI (bring your own key)
README writer, commit messages, release notes. Claude, OpenAI, Gemini, Groq, OpenRouter or any OpenAI-compatible API. The key is stored encrypted.

## Install
Download the APK from [Releases](../../releases) and open it. Requires Android 7.0 (API 24) or newer.

## Login
Use a **classic Personal Access Token** with `repo`, `workflow`, `delete_repo`, `read:org` and `user` (the in-app guide opens GitHub with them ticked), or sign in with **OAuth Device Flow** using your OAuth app's Client ID (enable *Device Flow* in the app settings).

| Scope | Used for |
|---|---|
| `repo` | Upload, edit, releases, Actions, secrets |
| `workflow` | Files in `.github/workflows`, running workflows |
| `delete_repo` | Deleting repositories |
| `read:org` | Organization repositories |
| `user` | Profile |

## Build with GitHub Actions
1. Upload all files of this project to a GitHub repository (keep `.github`).
2. Actions → **Build GitPush APK** → download `GitPush-debug-apk` / `GitPush-release-apk`.

### Release signing
Add secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`:

```bash
keytool -genkeypair -v -keystore release.jks -alias gitpush -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 release.jks
```

## Build locally
`./gradlew assembleDebug` (JDK 17 + Android SDK 34) · `./gradlew testDebugUnitTest`

## Project structure
```
app/src/main/java/com/gitpush/app/
├── data/      GitHubApi, Pusher, Stager, ZipTools, GitOps, AiClient, Store, Models
├── ide/       CodeEditText, WorkTree, Lint, IdePrefs
├── ui/        BaseActivity, Ui, GlassDialog, Icons, Rich, BarChartView, Downloader …
├── util/      PathUtils, ZipRules, BracketLint, SealedBox, GitHash (unit tested)
├── IdeActivity, ZipActivity, ActionsActivity, RunDetailActivity, JobLogActivity
├── SecretsActivity, ReleasesActivity, ImportRepoActivity, AiReadmeActivity …
└── KeepAliveService   foreground service for background work
app/src/main/res/      monochrome theme, drawables, 90+ vector icons, fonts
```

## Limits & notes
- Files over 50 MB are blocked (Git LFS); 25 MB+ shows a warning.
- IDE changes live in memory until you commit – the app asks before you leave with uncommitted changes.
- Code colors are off by default (monochrome); error marks are the only red in the app by design.
- Secrets need admin access to the repository; GitHub never shows secret values again.
- Encrypted (password-protected) ZIPs are not supported.

## License
MIT – see [LICENSE](LICENSE).
