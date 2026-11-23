# GitPush v2.2.0 — "Import Studio"

**Tag:** `v2.2.0`
**Title:** GitPush v2.2.0 "Import Studio" — clone repos like GitHub: tree, releases, Actions, secrets, history, issues + Sync Loop

## Highlights
- 📥 **Import Studio** — enter any repo URL (https, .git, SSH, /tree/branch), see files as a tree with tri-state checkboxes, plus releases, workflows, recent runs, secrets/variables, commits and issues
- ✅ Tick exactly what to copy — **nothing ticked = everything**
- 🧾 Pre-import dialog: name (availability checked), description, homepage, topics, public/private, existing-repo/folder target, then a **dry-run review**
- 🚀 Background engine with foreground notification, cancel, **resumable steps** and a Jobs screen (live log, Retry failed steps)
- 🕘 **History replay** (last 10–100 commits with original authors/dates) or one clean commit
- 🏷 Releases with notes, flags and assets · ⚡ Workflows pushed last with `[skip ci]` · 🔑 Variables copied, secrets re-entered and encrypted on device
- 💬 Issues, labels and milestones (mentions neutralised, paced for GitHub limits)
- 🔁 **Batch import** (loop over many URLs) and **Sync Loop** (auto-update copies, only changed files, one commit per run)
- 🛠 `gradlew` and other executables keep their executable bit

## Fixes
- Dialog buttons (Review / Cancel / Save…) were pushed off-screen by tall forms such as Import settings. Tall dialog content now scrolls inside the dialog, always leaves room for the buttons, and shrinks while the keyboard is open.
- Keyboard: Next/Done keys move through form fields, URL fields get the URL keyboard, name/topic/path fields turn off autocorrect, the Look up field has a Go key, the file search has a Search key, and the keyboard hides when you switch tabs or tap Import.

## Good to know
- GitHub never reveals secret values, has no API for Actions run history, and pull requests cannot be recreated. GitPush lists the secrets your workflows use so you can type them once.
- Tags in the new repo point at the import commit (or the last replayed commit).
- The workflow files need a token with the `workflow` scope.
- Not yet verified on a real device or an Actions build — please paste the first build log if anything fails.

## Install
Download `GitPush-v2.2.0.apk` below. Signed with your release keystore when the secrets exist.
