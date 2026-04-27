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

Drag the contents of this directory onto any static host. Pick whichever fits:

- **Cloudflare Pages** — connect this repo, set the build output directory to `website`, custom domain `cell-tower-id.com`. Easiest option for a custom domain.
- **GitHub Pages** — set Pages to serve from `website/` on `main`. Add a `CNAME` file with `cell-tower-id.com`.
- **Netlify / Vercel** — point at `website/` as the publish directory.
- **S3 / R2 / any bucket** — sync the directory and put a CDN in front.

## Things to swap before going fully live

1. **Play Store URL.** Buttons currently point at the deterministic `https://play.google.com/store/apps/details?id=com.terrycollins.celltowerid`. The link will 404 until the listing is published. If you launch the site first, consider relabelling the button to "Coming soon to Google Play" by editing the four `btn-cta` anchors in `index.html`.
2. **Open Graph image.** `og:image` references `https://cell-tower-id.com/assets/screenshots/02_map.png`. Once the domain is live the existing screenshot will work, but a custom 1200×630 social card looks better.
3. **Favicon.** Drop a `favicon.ico` (or `favicon.svg`) at the root and add `<link rel="icon" href="/favicon.svg">` to both HTML files.
4. **Analytics.** Intentionally omitted to match the app's privacy story. If you add anything, prefer a privacy-respecting option (Plausible, Fathom, Cloudflare Web Analytics).
5. **Privacy policy date.** `privacy.html` shows "April 16, 2026" — keep this in sync with `docs/privacy-policy.md` whenever you update either.

## Editing tips

- All copy lives in `index.html` &mdash; no template indirection.
- Threat-card text is verbatim from `app/src/main/java/com/terrycollins/celltowerid/domain/model/AnomalyType.kt` so the website matches what users see in-app. Update both together.
- Brand colors are CSS custom properties at the top of `styles.css`, mirroring `app/src/main/res/values/colors.xml`.
