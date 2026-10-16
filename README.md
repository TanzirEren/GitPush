<div align="center">

<img src="app/src/main/res/drawable-nodpi/app_icon_full.png" width="120" alt="GitPush icon" />

# GitPush

**Push files, folders and ZIPs to GitHub straight from your Android phone.**

`com.gitpush.app` · Android 7.0+ · Java + XML · Material 3

</div>

---

## 📖 Table of contents
1. [What is GitPush?](#-what-is-gitpush)
2. [Features](#-features)
3. [Install](#-install)
4. [First-time setup (login)](#-first-time-setup-login)
5. [How to use](#-how-to-use)
6. [How pushing works](#-how-pushing-works)
7. [Permissions & security](#-permissions--security)
8. [Build with GitHub Actions](#-build-with-github-actions)
9. [Release signing](#-release-signing)
10. [Build locally](#-build-locally)
11. [Project structure](#-project-structure)
12. [Limits & known issues](#-limits--known-issues)
13. [Troubleshooting](#-troubleshooting)
14. [Contributing](#-contributing)
15. [License](#-license)

---

## 🚀 What is GitPush?

GitPush is a lightweight Android client for **uploading to GitHub without a terminal**.
Pick files, a whole folder, or a ZIP archive from your phone, review exactly what will be pushed, write a commit message and push — all in **one clean commit**.

It is built for people who develop on their phone (or just need to drop files into a repo quickly): no Termux, no git commands, no laptop.

---

## ✨ Features

### 🔐 Account & login
- **GitHub OAuth (Device Flow)** – needs only a public *Client ID*; no client secret is ever stored in the app
- **Personal Access Token** login (classic `repo` scope or fine-grained *Contents: Read & write*)
- In-app, step-by-step guides for getting an OAuth Client ID and a Personal Access Token
- **Multiple accounts** – add another account and switch instantly
- Token scope and permission viewer
- Tokens stored encrypted with the **Android Keystore** (AES-256)

### 📦 Repositories
- Repository list with search and **Public / Private** filter
- Create new repositories
- Repo details: visibility, default branch, size, language, your permission (admin / write / read-only)
- Branch selector, create new branch
- Compare two branches and merge them

### ⬆️ Upload
- **Files & folders** picker – folder name and structure are kept exactly; empty folders get a `.gitkeep`
- **ZIP upload** – auto-extracted safely (path-traversal protected), then reviewed before pushing
- Review tree: expand folders, select items, remove selected, clear all
- Duplicate detection (same file picked twice is ignored)
- Target folder inside the repo
- Commit **message + description** with smart message suggestions
- Live progress with per-file status, **Cancel** and **Retry failed**
- Existing-file conflict dialog: **Replace / Skip / Cancel**
- Unchanged files are skipped automatically (Git SHA comparison)
- Large-file warning (25 MB) and block (50 MB)
- Network retry and GitHub rate-limit handling
- Completion / failure notifications
- Receive files from other apps via **Share → GitPush**

### 🧑‍💻 Repository browser & code editor
- Breadcrumb navigation and whole-repo file search
- Code viewer with line numbers and syntax colours
- Edit a file and commit the change
- Create, rename / move and delete files
- Long-press to multi-select and delete **files and folders** in one commit
- Image preview and Markdown preview
- Download a file, copy path / raw URL, per-file history

### 🗜️ Advanced ZIP extractor (new in 1.1)
- Opens the ZIP **without extracting everything first** and lists every file path
- Choose the **root folder** to upload from – e.g. `Buildora-v2.0.0/Buildora/others` → start at `Buildora/` or `others/`
- One-tap "Start at …" chips for nested wrapper folders, **strip N folders**, add a folder prefix
- Filters: exclude / include globs (`*.class, build/, node_modules`), skip hidden files, skip junk (`.DS_Store`, `__MACOSX`), flatten
- Per-file path review (original → upload path), size, search, select all / none / invert, long-press for full path, share the path list

### ⚡ GitHub Actions (new in 1.1)
- Workflow runs with live status (auto-refresh while running), jobs and steps
- Re-run all / failed jobs, cancel, delete runs
- **Download artifacts** (your APK) and logs straight to your phone
- Run a workflow (`workflow_dispatch`), enable / disable workflows

### 🏷️ Releases (new in 1.1)
- Create / edit / publish drafts, pre-releases, delete
- Notes: template, GitHub auto-generated, or ✨ AI-written from your commits
- Upload assets (APK, ZIP …) with progress, manage and delete assets

### ✨ AI assistant – bring your own key (new in 1.1)
- **AI README writer**: reads the file tree + key files, writes a README (style + language options), preview / edit / commit
- AI commit messages and AI release notes
- Works with Claude (Anthropic), OpenAI, Gemini, Groq, OpenRouter or any OpenAI-compatible API; key stored encrypted

### 🧰 More tools (new in 1.1)
- Favorite repos, edit repo info, topics, download repo ZIP, delete repo (type-to-confirm)
- Glass dashboard: 7-day push chart, latest build status, API rate-limit, quick actions
- Animated glass dialogs and screen transitions

### 🕘 History & tools
- Commit list, commit details and coloured diff viewer
- Revert a commit (single-parent commits)
- `.gitignore` generator (Android, Node, Python, Java, Web, macOS, Windows, JetBrains, VS Code)
- `README.md` generator
- SHA-256 and Git blob SHA checker
- Upload size calculator
- Upload history on the Home screen and a shareable report

### 🎨 Look & feel
- Animated splash, 3-page onboarding
- Floating **glass pill bottom navigation**: Home · ＋ · Profile
- Poppins font, Light / Dark / System theme, 5 accent colours
- Compact list density and haptic feedback options

---

## 📲 Install

1. Download the latest **APK** from the [Releases](../../releases) page (or from the *Artifacts* of a GitHub Actions run).
2. Open it on your phone. If asked, allow **Install unknown apps** for your browser / file manager.
3. Open GitPush, finish the intro and sign in.

**Requirements:** Android 7.0 (API 24) or newer.

---

## 🔑 First-time setup (login)

Choose one of the two methods on the login screen. The app also contains detailed guides (buttons at the bottom of the login screen).

### Option A – Personal Access Token (fastest)
1. GitHub → **Settings → Developer settings → Personal access tokens**.
2. Create a token:
   - *Classic (recommended)*: tick **`repo`**, **`workflow`**, **`delete_repo`**, **`read:org`** and **`user`**
     (the in-app guide opens GitHub with these already ticked).
   - *Fine-grained*: select your repositories, set **Contents → Read and write**, **Metadata → Read-only** (some features need more).
3. Copy the token (GitHub shows it only once).
4. In GitPush choose **Access Token**, paste it and tap **Sign in**.

### Option B – GitHub OAuth (Device Flow)
1. GitHub → **Settings → Developer settings → OAuth Apps → New OAuth App**.
   - Application name: `GitPush`
   - Homepage URL: any valid URL
   - Authorization callback URL: `https://github.com` (not used by Device Flow)
2. Open the new app and tick **Enable Device Flow**, then *Update application*.
3. Copy the **Client ID** (you do **not** need the client secret).
4. In GitPush choose **GitHub OAuth**, paste the Client ID and tap **Continue with GitHub**.
5. Tap **Copy code & open GitHub**, paste the code, press *Continue* and then *Authorize*. GitPush signs you in automatically.

> Organisation repositories with SSO: open the token page and use **Configure SSO → Authorize** for the organisation.

---

## 🧭 How to use

### Push files, a folder or a ZIP
1. Tap the big **＋** button in the bottom bar.
2. Choose a repository.
3. Pick the **branch** and (optionally) a **target folder** inside the repo.
4. Add content:
   - **Files & folders** → *Add files* / *Add folder*
   - **ZIP file** → *Select ZIP & extract*
5. Review the tree. Tap a folder to expand it, use the checkboxes to select items and **Remove** what you don't want.
6. Tap **Push**, write a commit message (and an optional description) and confirm.
7. If files already exist with different content you will be asked: **Replace / Skip / Cancel**.
8. When it finishes you can copy the commit SHA or open the commit on GitHub.

### Edit or delete files in a repo
- Repo list → **⋮ → Browse files**.
- Tap a file to view it; use **Edit** to change it and **Save** to commit.
- **Long-press** items to multi-select, then **Delete** to remove several files/folders in one commit.

### Share from other apps
In any app choose **Share → GitPush**. The files are staged and you only need to pick the repository.

---

## ⚙️ How pushing works

GitPush talks to the GitHub **Git Data API** instead of uploading file-by-file:

1. Check the branch (empty repositories are handled automatically).
2. Compare your files with the repository using Git blob SHAs – identical files are skipped.
3. Upload binary / large files as **blobs**; small text files are sent inline with the tree.
4. Build the new **tree** (in chunks) on top of the current branch tree.
5. Create **one commit** and move the branch pointer.

Because the commit is created only at the very end:
- ✅ cancelling or a network failure never leaves a half-pushed repo,
- ✅ **Retry** only re-uploads the failed files,
- ✅ no duplicate commits are created.

---

## 🔒 Permissions & security

| Permission | Why |
|---|---|
| `INTERNET` | Talk to the GitHub API (HTTPS only) |
| `POST_NOTIFICATIONS` (Android 13+) | Upload finished / failed notifications |

- Files and folders are chosen one by one with the **system file picker** – no broad storage permission is requested.
- Tokens are stored in `EncryptedSharedPreferences` backed by the Android Keystore. If the Keystore is unavailable, tokens are kept **in memory only** and never written in plain text.
- The OAuth flow uses only a public Client ID; **never** put a client secret in a mobile app.
- You can revoke access anytime in GitHub → *Settings → Applications* (OAuth) or by deleting the token.

---

## 🏗️ Build with GitHub Actions

1. Create a repository and upload **all files of this project** (keep the `.github` folder).
2. Open the **Actions** tab → **Build GitPush APK** (it also runs on every push to `main` / `master`, or press **Run workflow**).
3. When it finishes, open the run and download:
   - `GitPush-debug-apk`
   - `GitPush-release-apk` (signed if you added the secrets below)

The workflow uses JDK 17, Gradle 8.7 and the Android SDK that is preinstalled on `ubuntu-latest`.

---

## ✍️ Release signing

To sign the release APK with **your own key**, add these repository secrets
(*Settings → Secrets and variables → Actions → New repository secret*):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Your keystore file encoded in Base64 |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Create a keystore (once) and encode it:

```bash
keytool -genkeypair -v -keystore release.jks -alias gitpush \
  -keyalg RSA -keysize 2048 -validity 10000

# Linux / Termux
base64 -w 0 release.jks
```

Paste the printed text into `KEYSTORE_BASE64`.
If the secrets are missing, the release APK is signed with the debug key so it is still installable.

> ⚠️ Keep your keystore and passwords safe. If you lose the key you cannot update the app for users who installed the signed version.
> Do **not** commit `*.jks` / `*.keystore` files (they are already in `.gitignore`).

---

## 🛠️ Build locally

Requirements: **JDK 17** and the **Android SDK** (API 34).

```bash
./gradlew assembleDebug      # APK → app/build/outputs/apk/debug/
./gradlew assembleRelease    # APK → app/build/outputs/apk/release/
./gradlew testDebugUnitTest  # unit tests
```

---

## 🗂️ Project structure

```
app/src/main/java/com/gitpush/app/
├── data/
│   ├── GitHubApi.java      # GitHub REST + Git Data client (OkHttp)
│   ├── OAuthDevice.java    # OAuth Device Flow
│   ├── Pusher.java         # Push engine (blobs → tree → one commit)
│   ├── Stager.java         # Files / folders / ZIP staging + tree model
│   ├── ZipTools.java       # Random-access ZIP listing + selective extraction
│   ├── AiClient.java       # Anthropic / OpenAI-compatible client
│   ├── GitOps.java         # Multi-file delete, rename, revert
│   ├── Store.java          # Encrypted token + settings storage
│   └── Models.java         # Data classes
├── ui/
│   ├── BaseActivity.java   # Header, theming, async helper
│   ├── Ui.java             # View builders and dialogs
│   ├── Rich.java           # Syntax colours, Markdown, diff rendering
│   ├── GlassDialog.java    # Animated glass dialogs
│   └── ImageLoader.java    # Avatar loader
├── util/                   # Pure helpers (unit tested)
├── SplashActivity / OnboardingActivity / LoginActivity / GuideActivity
├── MainActivity + HomeTab / ReposTab / ProfileTab
├── UploadActivity / BrowserActivity / FileViewerActivity
├── CommitsActivity / CommitDetailActivity / ToolsActivity
├── ZipActivity             # Advanced ZIP extractor
├── ActionsActivity / ReleasesActivity / ReleaseEditActivity / AiReadmeActivity
├── ShareActivity           # "Share to GitPush" entry point
└── AppState / GitPushApp / Notifier
app/src/main/res/           # theme, glass drawables, icons, Poppins fonts
app/src/test/               # JVM unit tests
.github/workflows/build.yml # CI build + optional signing
```

**Stack:** Java 17 · AppCompat / Material 3 · OkHttp 4 · AndroidX Security Crypto · DocumentFile · Min SDK 24 · Target SDK 34

---

## ⚠️ Limits & known issues

- Single files over **50 MB** are blocked (use Git LFS); files over 25 MB show a warning.
- Symbolic links are not detected – everything is committed as a normal file.
- Revert supports single-parent commits only (not merge commits).
- Very large folders may take a while to scan.
- GitHub API rate limits apply; GitPush waits and retries when GitHub asks it to.

---

## 🧯 Troubleshooting

| Problem | What to do |
|---|---|
| `401` Token invalid / expired | Create a new token and sign in again |
| `403` Permission denied | Token lacks a scope (`repo`, `workflow` for .github/workflows files, `delete_repo`), or the organisation needs **SSO authorization** |
| `404` on a private repo | The token cannot see that repository – check its repository access |
| `422` Validation failed | Invalid path or a file/folder name clash in the repo |
| OAuth says "Device Flow" error | Enable **Device Flow** in your OAuth App settings |
| Upload shows "failed" files | Tap **Retry failed** – nothing was committed yet |
| Push is slow | Large files upload as blobs; stay on the screen and use a stable connection |

---

## 🤝 Contributing

Issues and pull requests are welcome.
Please include clear steps to reproduce bugs, your Android version and device model.

---

## 📄 License

Add your license here (for example MIT) and place a `LICENSE` file in the repository root.
