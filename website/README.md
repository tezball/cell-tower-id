# cell-tower-id.com

Static marketing site for the **Cell Tower ID** Android app. Plain HTML + CSS, no build step, no JS framework, no dependencies.

## Files

- `index.html` — landing page (hero, features, screenshots, threats, privacy summary, FAQ)
- `privacy.html` — standalone privacy policy (Play Store requires a public URL)
- `styles.css` — all styles
- `assets/screenshots/` — five product screenshots

## Local preview

Just open `index.html` in a browser:

```bash
open index.html
```

Or run a tiny local server if you want clean URLs:

```bash
python3 -m http.server 8080
# then visit http://localhost:8080
```

## Deploy

The site lives on an **AWS Lightsail** VPS (nginx), served at https://cell-tower-id.com.

### Automated (preferred)

Every push to `main` and every `v*.*.*` tag triggers the `deploy-website` job in [`.github/workflows/ci-cd.yml`](../.github/workflows/ci-cd.yml). It rsyncs `website/` over SSH to the Lightsail host using `easingthemes/ssh-deploy@v5`, then `curl`s `index.html`, `privacy.html`, and `licenses.html` to confirm 200s.

The job is gated on four GitHub Actions secrets — if any are missing the job logs a warning and skips, rather than failing the whole workflow:

| Secret | Example |
|---|---|
| `LIGHTSAIL_HOST` | `cell-tower-id.com` |
| `LIGHTSAIL_USER` | `ubuntu` |
| `LIGHTSAIL_SSH_KEY` | Contents of the deploy private key (full PEM) |
| `LIGHTSAIL_DEPLOY_PATH` | `/var/www/cell-tower-id.com/html` |

To rotate the deploy key, generate a new keypair (`ssh-keygen -t ed25519 -f ~/.ssh/lightsail_deploy -N ""`), append the public key to `~/.ssh/authorized_keys` on the VPS, and replace `LIGHTSAIL_SSH_KEY` in repo secrets. Remove the old public key from the server.

### Manual (one-off, if CI is down)

```bash
rsync -rlptDzv --delete --exclude=README.md \
  -e "ssh -i ~/.ssh/lightsail_deploy" \
  website/ ubuntu@cell-tower-id.com:/var/www/cell-tower-id.com/html/
```

## Things to swap before going fully live

1. **Play Store URL.** Buttons currently point at the deterministic `https://play.google.com/store/apps/details?id=com.celltowerid.android`. The link will 404 until the listing is published. If you launch the site first, consider relabelling the button to "Coming soon to Google Play" by editing the four `btn-cta` anchors in `index.html`.
2. **Open Graph image.** `og:image` references `https://cell-tower-id.com/assets/screenshots/02_map.png`. Once the domain is live the existing screenshot will work, but a custom 1200×630 social card looks better.
3. **Favicon.** Drop a `favicon.ico` (or `favicon.svg`) at the root and add `<link rel="icon" href="/favicon.svg">` to both HTML files.
4. **Analytics.** Intentionally omitted to match the app's privacy story. If you add anything, prefer a privacy-respecting option (Plausible, Fathom, Cloudflare Web Analytics).
5. **Privacy policy date.** `privacy.html` shows "April 16, 2026" — keep this in sync with `docs/privacy-policy.md` whenever you update either.

## Editing tips

- All copy lives in `index.html` &mdash; no template indirection.
- Threat-card text is verbatim from `app/src/main/java/com/celltowerid/android/domain/model/AnomalyType.kt` so the website matches what users see in-app. Update both together.
- Brand colors are CSS custom properties at the top of `styles.css`, mirroring `app/src/main/res/values/colors.xml`.
