# GitPush v2.3.0 — "Codespaces"

**Tag:** `v2.3.0`
**Title:** GitPush v2.3.0 "Codespaces" — create and manage Codespaces, mobile VS Code with key bar, run code with copyable output, AI helpers

## Highlights
- ☁ **Codespaces hub** — status, repo, branch, machine, last used, git-dirty indicator; filter, search, auto-refresh
- 🚀 **Create** guided / quick / from a pull request (machine types with specs and core-hour rate, dev container, region, idle timeout, retention, name) with a permissions check
- ⏯ Start, stop, rename, change machine, delete, **bulk** stop/delete, stop all, export to a branch, publish to a new repo
- 💻 **Mobile VS Code** — VS Code Web in a WebView with progress screen, layout width, **key bar** (Esc, Tab, Ctrl grid, Alt, Shift, arrows, symbols, ^C/^D/^L/^R/^Z, palette, copy/paste), full screen, port preview, tablet split view
- ▶ **Terminal pad** — run code in 11 languages in the codespace terminal; native **Output** with long-press select / copy; selection menu with **Run**, **Explain**, **Save snippet**; snippets, history, favorites, ports
- ✨ **AI** (own key) — words to command, explain output, fix code, AI dev container commit. Risky commands always ask first
- 🔑 **Codespaces secrets** (account with repo access, or per repo) · 🏢 **Organization codespaces** · ⏱ stop-when-I-leave and still-running reminders

## Good to know
- GitHub has **no API** for terminal, files or editor inside a codespace, for port visibility, or for terminal output. GitPush embeds VS Code Web and injects keys/paste (best effort) and mirrors run output via a read-only server on port 7799 inside the codespace.
- Sign in again (or tick `codespace` on a PAT) so the token has the new scope.
- Not yet tested on a real device or an Actions build — please paste the first build log or a screenshot if anything fails.

## Install
Download `GitPush-v2.3.0.apk` below. Signed with your release keystore when the secrets exist.
