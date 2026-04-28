# Cell-Tower Battle: Design Summary (Parked)

> **Status:** Parked for later. This document is a self-contained summary of the idea, design decisions, and how the game works from the user's perspective, so the idea can be picked up cold in a future session.

## What the idea is

A separate Android game, sister to the existing **Cell Tower ID** security app, in which **finding real cell towers powers a 2D shooter** (Metal Slug as a style reference, not a source). Discovering a tower drops an era-appropriate weapon. The player then walks to a nearby safe GPS point to fight a battle, progressing through increasing difficulty. Crowd-sourced tower discoveries feed the shared tower catalog — the game's primary business value is incentivizing people to map cellular infrastructure.

**The central hook:** real cellular tech progression (GSM → LTE → NR) maps naturally onto the game's narrative arc (past → present → sci-fi future). Finding a 5G tower thematically earns a plasma rifle.

## Key design decisions locked in

- **Separate app**, not a mode inside the security app. Shares a `:core-tower` + `:core-data` library extracted from the existing codebase.
- **Local-only MVP.** No backend, no accounts, no automatic upload, no IAP.
- **Original art and terminology.** Metal Slug / Pokémon are *references*, not sources. No Niantic-adjacent capture mechanics (no projectiles-at-creatures).
- **Game data lives in its own Room DB.** Shared tower catalog is read-mostly from the game's perspective; writes only happen via a vetted submission path.
- **Tower submissions allowed**, but filtered. [`AnomalyDetector`](app/src/main/java/com/celltowerid/android/service/AnomalyDetector.kt)'s GPS-vs-tower and impossible-move checks are reused client-side to drop obvious spoofs before anything gets written.
- **MVP ships present-day era only.** Past and future eras are post-MVP content packs.
- **Player-confirmed battlegrounds** (not random GPS) — the player is the final filter for "is this a safe place to stand and play." Protects against the freeway/cemetery problem Niantic famously hit.

## How the game works (user journey)

### First-time user

1. Install, open. Welcome screen: *"This game maps real cell towers. Walk around, find towers, fight battles."*
2. Grant permissions: fine location, notifications, foreground service.
3. Onboarding explains the era the player is in (present) and hands them a starter weapon (basic pistol).
4. **Map screen** appears — player's GPS dot, nearby known towers rendered as era-tinted icons, "Start Session" button.
5. Player taps **Start Session** → foreground service starts with a persistent notification.
6. Player walks outside and approaches a known tower. When the tower is within walking distance *and* the device confirms it via `getAllCellInfo()`:
   - **Resupply event**: brief animation, `Resupply: LTE · Band 4 · Sector 2 · Assault Rifle Mk II` (weapon flavored by real tower attributes).
   - Weapon added to inventory.
7. Once at least one weapon is earned, the app proposes **2–3 candidate battleground points** within ~100–300 m. Player picks one and explicitly confirms *"I can safely be here."*
8. Player walks to the chosen point (distance + direction hint on screen).
9. On arrival: *"Ready to fight? [Start Battle]"* Player taps → map screen dissolves into the **2D shooter arena**.
10. **Combat:** waves of era-appropriate enemies; virtual d-pad + fire button; player HP bar; active weapon slots pulled from inventory. Win = XP + tier advance. Lose = back to map with loadout intact.
11. After victory, a new battleground spawns elsewhere on the map at the next difficulty tier.
12. **Discovery moment:** if the player wanders somewhere the catalog doesn't know about, a detected new tower fires a **Discovery event** — rarest weapon drop, and the measurement is queued in `PendingSubmissions` for later export.
13. Session ends when the player taps **End Session** or the app auto-ends after inactivity. **Session summary:** towers found, battles won, weapons earned, XP gained.

### Returning user (steady state)

1. Open app → last position, active battleground if any, current loadout.
2. Check **Journal** (collection grid) and active **Field Challenges** (locally-rotating weekly goals like "win 3 battles with present-era weapons").
3. Tap **Resume Session** → foreground service restarts.
4. Out and about: discover new towers, re-survey known ones for fresh weapon rolls, complete battles, advance difficulty tier.
5. Manage **loadout** from the Journal — swap which weapons occupy active slots.

### The developer-facing submission flow (MVP)

1. As the player discovers towers, entries build up in `PendingSubmissions`.
2. Player opens **Settings → Export Tower Data**, generates a file (JSON or CSV).
3. Player shares the file with the developer out-of-band.
4. Developer vets and merges into the shared catalog in a later app update.

Post-MVP, this becomes an automatic upload with server-side corroboration (tower confirmed once independently observed by M distinct devices).

## Main screens

- **Map** — primary screen. Player location, known towers, active battleground, Start/End Session.
- **Survey / Resupply overlay** — shown transiently on tower detection.
- **Battle arena** — the 2D shooter view.
- **Journal** — collected weapons grid, filterable by era/tech/rarity, with flavor text tied to the tower of origin.
- **Profile** — level, XP, tier, lifetime stats, active Field Challenges.
- **Session summary** — shown at session end.
- **Settings** — permissions, data export, attribution to the sibling security app.

## Engine decision: **LibGDX** (locked in)

> Decided during planning — when this is picked up again, start from LibGDX. Rationale follows. Godot remains the documented fallback if LibGDX proves impractical once prototyping begins.

## Engine evaluation (for commercial release + AI-assisted development)

Evaluated against three criteria: (1) licensing terms for selling the game, (2) how well AI coding assistants can generate code for it, and (3) integration with the existing Kotlin Android codebase so `:core-tower` can be shared instead of duplicated.

### LibGDX — recommended

- **Licensing:** Apache 2.0. No royalties, no revenue caps, sell forever. Cleanest possible commercial story.
- **Language:** Kotlin (or Java). AI models are exceptionally fluent in Kotlin/Android — this produces the highest-quality AI-assisted output of any option here.
- **Android integration:** Best of all options. LibGDX is a library, not a separate engine — the game is a Gradle module in the same project as the security app. Native Android UI (Compose) for map/menu/inventory, LibGDX `ApplicationAdapter` hosted in an Activity for the battle arena. Consumes `:core-tower` directly.
- **2D shooter fit:** Proven genre fit (Scene2D + Box2D). Many shipped examples.
- **APK weight:** Smallest overhead.
- **Tradeoffs:** Tooling is programmer-first — no scene editor on par with Unity/Godot. Sprite pipeline is manual. AI-generated assets still drop in cleanly (PNG atlases), but level/scene layout is code, not a visual editor. Community smaller than Unity or Godot.
- **Verdict:** Best engineering fit *for this project specifically* because of the shared Kotlin codebase.

### Godot — strong alternative

- **Licensing:** MIT. Completely free, no royalties, no revenue caps, no strings. Equal to LibGDX on licensing cleanliness.
- **Language:** GDScript (Python-ish) or C#. AI models are fluent in both; GDScript benefits from Python training overlap, C# from general training volume.
- **Android integration:** Separate runtime. The game is its own Godot-built app. Sharing `:core-tower` requires either a JNI bridge (complex) or duplicating the detection logic in GDScript/C# (ongoing maintenance cost).
- **2D shooter fit:** Native 2D pipeline (not bolted onto 3D like Unity's was historically). Clean scene editor, tilemaps, particle system, physics. Excellent for this genre.
- **APK weight:** ~20 MB base. Acceptable.
- **Tradeoffs:** Losing the `:core-tower` share is the main hit. Smaller asset marketplace than Unity. Rapidly improving ecosystem — much more mature than it was three years ago.
- **Verdict:** The pick if you value best-in-class 2D tooling + cleanest licensing more than Kotlin code-sharing.

### Unity — safe-but-uncertain

- **Licensing:** Free (Personal plan) under $200K/yr revenue. Above that, Unity Pro ~$2,040/seat/year. **Real concern: the 2023 "Runtime Fee" proposal** (a per-install fee retroactively applied) was ultimately walked back, but the fact that it was attempted signals real future pricing risk. For a commercial title that might run for years, this is not negligible.
- **Language:** C#. AI models are extremely fluent — probably the best-documented game-dev language for AI assistance.
- **Android integration:** Worst of the big three for this project. Unity runs in its own process/framework; embedding Unity scenes into an existing Android app ("Unity as a Library") is supported but awkward and adds ~30+ MB to the APK. Sharing Kotlin code is impractical — you'd duplicate detection logic in C#.
- **2D shooter fit:** Excellent. Massive tutorial library, asset store, many Metal Slug-style starter kits.
- **Tradeoffs:** Tooling and community are the biggest of any option. But: licensing history + Android integration pain are real costs.
- **Verdict:** Reasonable if you value maximum tooling and the Kotlin share isn't a priority, but recognize the licensing trajectory is uncertain.

### Other options considered and rejected

- **Flutter + Flame** (BSD + MIT licenses). Clean commercial terms, but Dart is weaker for AI assistance than Kotlin/C#/GDScript, and there's no reason to pay the cross-platform cost when the target is Android only.
- **Cocos2d-x / Cocos Creator** (MIT). Capable 2D engine, strong in Asia, but smaller Western indie community and less asset availability. Licensing is fine; ecosystem argues against it.
- **Defold** (Developer Friendly License — free for commercial, with reporting requirements for some revenue bands). Technically capable but Lua + Defold-specific APIs are poorly represented in AI training data.
- **Compose + Canvas** (Apache 2.0). Wrong tool. Fine for Tetris, painful for a Metal Slug-style shooter. No sprite batcher, no physics, no particle system — you'd build everything by hand.

### Side-by-side on the three criteria

| Engine | Licensing for sale | AI-code fluency | Share `:core-tower` with existing app |
|--------|-------------------|-----------------|---------------------------------------|
| **LibGDX** | Apache 2.0, no strings | Excellent (Kotlin) | **Yes — direct Gradle dep** |
| **Godot** | MIT, no strings | Strong (GDScript / C#) | No — duplicate or JNI bridge |
| **Unity** | Free <$200K; history of pricing changes | Excellent (C#) | No — Unity-as-Library, heavy |
| Flutter + Flame | BSD + MIT | Mid (Dart) | Possible via Flutter module, awkward |
| Cocos2d-x | MIT | Mid | No |
| Defold | Free, reporting | Weaker | No |

### Concrete recommendation

**LibGDX** for this project, because:
1. The existing Cell Tower ID app is already Kotlin — `:core-tower` becomes a shared Gradle module dependency with zero bridging cost.
2. Apache 2.0 licensing is future-proof for commercial sale (no revenue threshold, no rug-pull risk).
3. AI assistance is strongest in Kotlin/Android.
4. Smallest APK overhead.

**Godot** as a very close second if Kotlin code-sharing isn't worth trading away best-in-class 2D tooling. Rewrite tower detection in C# or call into Kotlin via JNI — either is manageable, just additional work.

**Unity** only if the team has deep existing Unity experience and accepts the licensing trajectory risk.

## Open questions to resolve when this is picked up again

1. ~~**Battle engine.**~~ **Resolved: LibGDX.**
2. **Art strategy.** Stock asset packs (fast, generic), commissioned original sprites (slow, on-brand), or AI-assisted + human-edited (cheap, legally and stylistically fraught).
3. **Difficulty curve seed values.** E.g. each tower = +10% weapon tier; each battle = +15% enemy HP. Needs initial numbers to playtest.
4. **Session length target.** 5 min (quick walk) vs 20 min (commute) — drives battleground density and pacing.
5. **Battleground acquisition upgrade.** Confirm player-confirmed candidates is adequate for MVP, and when OSM-based public-space filtering becomes worth the integration cost.
6. **Monetization decision.** Whether to instrument for IAP in MVP (so "weapon packs" can slot in later) or defer entirely.

## Honest scope note

This is a meaningful undertaking. A 2D shooter is its own engine subproject, and even one era of original sprite art (player animations, ≥3 enemy types with multiple states, weapon FX, backgrounds, UI) is weeks of art work. Realistic MVP for a motivated solo developer is measured in **months, not weeks**. Worth reconfirming appetite before committing to a build.

## Relevant files in the existing codebase (for when the build plan happens)

- [CellInfoProvider.kt](app/src/main/java/com/celltowerid/android/service/CellInfoProvider.kt) — shared detection interface
- [RealCellInfoProvider.kt](app/src/main/java/com/celltowerid/android/service/RealCellInfoProvider.kt) — `getAllCellInfo()` wrapper to reuse
- [CellMeasurement.kt](app/src/main/java/com/celltowerid/android/domain/model/CellMeasurement.kt) — shared domain model
- [AnomalyDetector.kt](app/src/main/java/com/celltowerid/android/service/AnomalyDetector.kt) — reused as client-side spoof filter on game submissions
- [CollectionService.kt](app/src/main/java/com/celltowerid/android/service/CollectionService.kt) — reference pattern for the game's own foreground service
- `docs/02-android-cellinfo-api.md` — CID/NCI sector decomposition (for sector-as-weapon-slot)
- `docs/03-signal-metrics-reference.md` — RSRP/RSRQ thresholds (for weapon-tier quality logic)