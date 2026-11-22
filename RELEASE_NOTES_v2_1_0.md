# GitPush v2.1.0 — "Control Center"

**Tag:** `v2.1.0`
**Title:** GitPush v2.1.0 "Control Center" — release order, signing keys, 18 GitHub controls, download fix

## Highlights
- 🏷 **Release tags can be changed** (rename, tag a commit, move to branch head, delete old tag, mark Latest)
- 🔁 **Release Order Studio** — put any release first/last/anywhere, with undo
- 🔑 **APK signing tool** — generate keystores (.p12/.jks/.bks), fingerprints, push the 4 secrets to many repos, key vault, import & convert, APK certificate inspector
- 🖼 **Social preview studio** — 1280×640 image ready for GitHub's settings page
- ⚙ **Control center** — Issues, PRs, Branches/tags/protection, Collaborators, Webhooks, Deploy keys, Labels/milestones, Insights, Repo settings & security, Pages, Actions cleaner, Environments + secrets, Security alerts, Gists, Notifications, Starred, Bulk repo actions, Search, Profile editor
- 💻 **Code IDE** — preview icon beside Save, bulk selection (download / delete / move / copy)
- ⬇ **Downloads fixed** — no more `name.zip 1`; real MIME types, `name (1).zip` on clashes, remembered folder
- 📋 **Job logs** — select and copy like the GitHub website
- 🎨 New app icon

## Good to know
- GitHub orders releases by the **date of the tag's commit**. Reordering adds a small "date anchor" commit (same files, new date) per moved tag and re-points the tag. Annotated tags become lightweight. Undo restores the previous targets.
- GitHub has **no API for the social preview upload**; GitPush prepares the file and opens the settings page.
- Not yet verified on a real device or an Actions build — please paste the first build log if anything fails.

## Install
Download `GitPush-v2.1.0.apk` below. Signed with your release keystore when the secrets exist.
