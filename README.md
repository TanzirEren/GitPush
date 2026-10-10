# GitPush (com.gitpush.app) – Java edition

Android app written in **Java + XML resources** (AppCompat / Material 3 theme, no Kotlin, no Compose) to push files,
folders and ZIPs to GitHub from your phone.

## Build with GitHub Actions
1. Create a new GitHub repository and upload **everything in this folder** (keep the `.github` folder).
2. Open the **Actions** tab → "Build GitPush APK" runs on every push (or press *Run workflow*).
3. When it finishes open the run → **Artifacts** → download `GitPush-debug-apk` and install it.

Optional signed release: add repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Local build: `./gradlew assembleDebug` (JDK 17 + Android SDK).

## Login
* **Personal Access Token** – paste a token with `repo` scope.
* **OAuth (Device Flow)** – create an OAuth App at github.com/settings/developers, tick *Enable Device Flow*, paste the
  **Client ID** in the app. No client secret is ever stored.

## How pushing works
Files are uploaded as Git blobs/trees and a **single commit** is created at the end, so cancel/fail never leaves a
half-finished push. Unchanged files are skipped by SHA compare; existing files ask Replace / Skip / Cancel.

## Code map (`app/src/main/java/com/gitpush/app`)
* `data/` – `GitHubApi` (OkHttp), `Pusher` (push engine), `Stager` (files/folders/ZIP), `GitOps`, `Store` (encrypted tokens)
* `ui/` – `BaseActivity`, `Ui` (view builders), `Rich` (syntax colors, Markdown, diff), `ImageLoader`
* Activities: Splash, Onboarding, Login, Guide, Main (+ HomeTab / ReposTab / ProfileTab), Upload, Browser, FileViewer,
  Commits, CommitDetail, Tools, Share
* `util/` – pure helpers with unit tests (`app/src/test`)
* Theme, colors, glass drawables, icons and Poppins fonts live in `res/`
