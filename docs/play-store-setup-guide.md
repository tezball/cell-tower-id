# Google Play Store — Personal Account Setup Guide

## Step 1: Create a Google Play Developer Account

1. Go to https://play.google.com/console/signup
2. Sign in with your Google account
3. Select **Personal** account type
4. Pay the **$25 one-time** registration fee
5. Complete identity verification (government-issued ID required)

Verification can take 24-48 hours.

## Step 2: Create Your App

1. In Play Console, click **Create app**
2. Fill in:
   - **App name:** Cell Tower ID
   - **Default language:** English (United Kingdom)
   - **App or game:** App
   - **Free or paid:** Free
3. Accept the declarations and click **Create app**

## Step 3: Set Up Your Store Listing

Go to **Grow > Store presence > Main store listing**

Copy text from `docs/play-store-listing.md` in the repo:
- **Short description:** Map cell towers, track signal strength, and detect IMSI catchers on your device.
- **Full description:** Copy the full description section

### Graphics

Upload from the `screenshots/` folder in the repo:

| Asset | Requirement | File |
|-------|------------|------|
| App icon | 512x512 PNG | Use Android Studio to export (File > New > Image Asset) |
| Feature graphic | 1024x500 PNG | Create in Canva/Figma or similar |
| Phone screenshots | Min 2, 1080x1920+ | `screenshots/01_onboarding.png` through `05_settings.png` |

For better screenshots, take them from your Samsung with real cell data.

## Step 4: Content Rating

Go to **Policy > App content > Content rating**

1. Click **Start questionnaire**
2. Select **Utility, Productivity, Communication, or Other** category
3. Answer **No** to all content questions (violence, sexual content, etc.)
4. Save and submit — you should get an **Everyone** rating

## Step 5: Data Safety

Go to **Policy > App content > Data safety**

Use the answers from `docs/play-store-listing.md` (Data Safety Form section):

1. **Does your app collect or share user data?** Yes
2. **Data types collected:**
   - Approximate location — Yes, collected, not shared
   - Precise location — Yes, collected, not shared
3. **Is data encrypted in transit?** N/A (no data transmitted)
4. **Can users request data deletion?** Yes
5. **Is the app designed for children?** No

## Step 6: Privacy Policy

Go to **Policy > App content > Privacy policy**

Enter: `https://cell-tower-id.com/privacy.html`

## Step 7: Target Audience

Go to **Policy > App content > Target audience**

- Select **18 and over** (simplest — avoids children's policy requirements)

## Step 8: App Access

Go to **Policy > App content > App access**

- Select **All functionality is available without special access**
- No login or credentials needed to review the app

## Step 9: Ads Declaration

Go to **Policy > App content > Ads**

- Select **No, my app does not contain ads**

## Step 10: Upload the Release

Go to **Release > Production**

1. Click **Create new release**
2. If prompted, opt in to **Play App Signing** (recommended — Google manages your signing key, you keep the upload key)
3. Upload the AAB file: `app/build/outputs/bundle/release/app-release.aab`
4. Add release notes:
   ```
   Initial release of Cell Tower ID v1.0.0
   
   - Interactive cell tower map with signal heatmaps
   - Real-time signal strength tracking
   - IMSI catcher detection with 7-point anomaly scoring
   - Tower locator with directional guidance
   - CSV, GeoJSON, and KML data export
   - Fully offline — all data stays on your device
   ```
5. Click **Review release** then **Start rollout to Production**

## Step 11: Review

Google reviews new apps, typically within 1-3 days. You'll get an email when approved or if changes are needed.

Common rejection reasons to watch for:
- **Sensitive permissions:** The background location declaration will be reviewed. The prominent disclosure dialog we added should satisfy this.
- **Privacy policy:** Must be accessible at the URL provided. Verify https://cell-tower-id.com/privacy.html loads correctly.
- **Metadata:** Store listing must accurately describe the app.

## Useful Links

- Play Console: https://play.google.com/console
- Developer account signup: https://play.google.com/console/signup
- Play Console help: https://support.google.com/googleplay/android-developer
- Policy center: https://play.google.com/console/about/policy-center
- Data safety guide: https://support.google.com/googleplay/android-developer/answer/10787469
- Content rating: https://support.google.com/googleplay/android-developer/answer/188189
- App signing: https://support.google.com/googleplay/android-developer/answer/9842756
- Background location policy: https://support.google.com/googleplay/android-developer/answer/9799150

## Files in This Repo

| What | Where |
|------|-------|
| Release AAB | `app/build/outputs/bundle/release/app-release.aab` |
| Screenshots | `screenshots/` |
| Store listing text | `docs/play-store-listing.md` |
| Privacy policy | `website/privacy.html` (served at https://cell-tower-id.com/privacy.html) |
| Keystore credentials | `keystore.properties` (local only, not in git) |
