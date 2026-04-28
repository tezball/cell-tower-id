# Cell Tower ID — Release Checklist

Run through this for every release. The first time (v1.0.0), some one-time setup steps apply; subsequent releases skip those.

---

## One-time setup (v1.0.0 only)

### A. GitHub Actions secrets

The release workflow at `.github/workflows/release.yml` needs four secrets to sign the AAB. Add them at *GitHub → repo settings → Secrets and variables → Actions*:

| Secret | Value source |
|---|---|
| `SIGNING_KEY_BASE64` | `base64 -i release.keystore` (paste the whole blob) |
| `SIGNING_STORE_PASSWORD` | `storePassword` from local `keystore.properties` |
| `SIGNING_KEY_ALIAS` | `upload` |
| `SIGNING_KEY_PASSWORD` | Same as `SIGNING_STORE_PASSWORD` (PKCS12 keystores share one password) |

### B. (Optional but recommended) Play Console service account

Lets the release workflow auto-upload the AAB to the Internal test track. Skip this and you'll download the AAB artifact and upload by hand.

1. Go to [Google Cloud Console](https://console.cloud.google.com/) → create a new project (or reuse one).
2. Enable the *Google Play Android Developer API* for that project.
3. *IAM & Admin → Service Accounts → Create service account*. Name it `play-publisher`. Skip role assignment in GCP.
4. Open the new service account → *Keys → Add key → JSON*. Download the JSON file.
5. Open [Play Console → Setup → API access](https://play.google.com/console/api-access). Link the GCP project. Find the service account in the list and grant it *Releases → Manage releases* permission for this app only.
6. Add the entire JSON content (one line, no escaping) as GitHub secret `PLAY_SERVICE_ACCOUNT_JSON`.

After this, every tag push automatically creates a draft release on the Play Internal track.

### C. Play Console initial setup

Done in the browser, one-time:

- [ ] App created in Play Console (package `com.celltowerid.android`)
- [ ] Privacy policy URL set: `https://cell-tower-id.com/privacy.html`
- [ ] Store listing populated from `docs/play-store-listing.md` (short desc, full desc, screenshots from `screenshots/`, app icon, feature graphic)
- [ ] Content rating questionnaire submitted (see `docs/play-console-declarations.md`)
- [ ] Data safety form submitted (see `docs/play-console-declarations.md`)
- [ ] Permissions declarations submitted for `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE_LOCATION` (paste from `docs/play-console-declarations.md`)
- [ ] Pricing set to €2.50 EUR with auto-conversion enabled
- [ ] Closed test track created with at least 20 testers (mandatory 14-day clock for new individual developer accounts)

---

## Per-release checklist

### Pre-tag

- [ ] Working tree is clean: `git status` shows nothing
- [ ] Local `main` is up to date with `origin/main`: `git fetch && git status`
- [ ] All planned changes are merged

### Tag

- [ ] Run `./gradlew lint test` locally — must be green
- [ ] Run `./gradlew bundleRelease` locally — must produce `app/build/outputs/bundle/release/app-release.aab`
- [ ] Smoke-test the signed APK on a real device:
  - [ ] `./gradlew assembleRelease`
  - [ ] `adb install -r app/build/outputs/apk/release/app-release.apk`
  - [ ] Onboarding completes
  - [ ] Foreground location permission grant flow works
  - [ ] Background location permission grant flow works (Android 10+)
  - [ ] Notification permission grant flow works (Android 13+)
  - [ ] Map loads with tiles
  - [ ] Start collection → notification appears with *Stop* action → measurements show in Cells tab → stop collection cleanly
- [ ] Update `CHANGELOG.md` with the new version's changes
- [ ] Update `fastlane/metadata/android/en-US/changelogs/N.txt` (where N matches the new versionCode) with the 500-char Play release notes
- [ ] Bump version: `./scripts/bump-version.sh 1.0.X`
- [ ] Push: `git push --follow-tags`

### Post-tag

- [ ] Watch the [release workflow](https://github.com/tezball/cell-tower-id/actions/workflows/release.yml) succeed
- [ ] Verify the AAB artifact uploaded (Actions tab → workflow run → artifacts)
- [ ] If `PLAY_SERVICE_ACCOUNT_JSON` is set: confirm draft release appeared in Play Console Internal track
- [ ] If not set: download AAB artifact, upload manually to Play Console Internal track
- [ ] Promote Internal → Closed → Production through the normal Play Console flow

### Rollback plan

Play Store releases are **not instantly reversible**. If a critical bug ships:

1. **Halt rollout** in Play Console (*Production → Manage track → Halt rollout*) — stops the percentage rollout from progressing.
2. **Submit a fix release** with a higher versionCode. Once approved, it replaces the broken one for new installs and updates.
3. Communicate to existing users via the *What's new* notes in the next release; there is no in-app messaging mechanism.
4. There is **no rollback to a previous versionCode** on Play Store — the only path forward is a new, higher versionCode.

### Common policy rejection causes (and fixes)

| Rejection | Fix |
|---|---|
| Missing background location justification | Update `docs/play-console-declarations.md` and re-submit the declaration |
| Data safety mismatch (Google detected something we didn't declare) | Re-audit `docs/play-console-declarations.md` against actual code; update declaration |
| Foreground service type mismatch | Confirm `android:foregroundServiceType` in `AndroidManifest.xml:60` matches actual usage |
| Target SDK too low | Update `targetSdk` in `app/build.gradle.kts:19` (Play requires within 1 year of latest Android release) |
| Permissions declared but not used | Remove unused `<uses-permission>` from `AndroidManifest.xml` |
