# PROGRESS — Playable Alpha

**Branch:** `work/playable-alpha`
**Last updated:** 2026-10-13 (owner playtest session recorded; fixes deferred to batch)
**HEAD at completion:** see `git log`; PR opened from this branch to `main`.

## Final status

Deliverable report: **docs/DELIVERABLE_REPORT.md** (implemented/verified/not-verified lists).
Decisions: **docs/DECISIONS.md**.

Verified runtime evidence (automated protocol client through the offline Velocity, plus console probes):
- Growth Pickaxe: first-join grant, real mining progress (uniform 1.00/break), evolution to
  Iron observed live, status reached `tier=Diamond progress=424/1200`, persistence across
  restarts, `status`/`branch`/`repair` commands, WM deduction on repair (5000 → 4971).
- Waymark shared economy: balance credited on Main read from Frontier (5000 WM).
- Frontier: LeafGrapple adapter active, gates built, gate-plate teleport into frontier_iris,
  loadout granted (elytra/grapple hook/launchpads/navigation).
- All production plugin jars enabled on all backends; frontier_iris (Iris engine, custom
  biome source injected), frontier_bs (HARD) exist.

**Second delivery (2026-10-11, Owner-provided artifacts, all sha256 verified):**
- EconomyShopGUI 7.1.1 on Main, hooked to WaymarkProvider (`Successfully hooked into Vault and
  using WaymarkProvider as economy provider`), owns `/shop`.
- Iris official 3.9.2 jar replaces the source build; EliteMobs.jar 10.7.3 + BetterHealthBar
  4.1.0 on Frontier; BetterStructures + 5 content packs on Main/Frontier (`fully initialized`);
  LuckPerms 5.5.60 on all five servers (local storage).
- **Redis 7 + MariaDB installed by Owner.** RedisEconomy is now the live economy provider on both
  servers (isolated Redis :6399, cluster waymark): cross-server shared Waymark verified
  (2800 WM credited on Main, read on Frontier), EconomyShopGUI consumes it.
  AdvancedPortals 2.8.0 deployed on proxy+backends (same binary; portal creation is in-game only).
  EliteMobs content imported (custom items loading). LuckPerms 5.5.60 on all servers.
  HeadlessMC: official 1.21.4/26.2 clients boot headless; auto-join blocked in HMC RC5 — Owner's
  official client remains the playtest vehicle.
  **MariaDB shared storage LIVE**: LuckPerms (mysql + sql messaging on all 5 servers) and
  mcMMO (wayfarer_mcmmo, mcmmo_ prefix) — cross-server verified (mining 500 XP set on Main,
  read on Frontier via inspect; LP group created on Main visible network-wide).
  ResourcePackManager port collision fixed (selfHostPort 25591 on Main; auto MC+1 collides with
  Frontier's 25580). CoreProtect-CE 24.0 cannot start on MC 26.2 (upstream).

Remaining verification work (documented as NOT verified):
- Waystone/launchpad interactive flows (bot use-item-on packets don't reach the 1.21.11
  backend through ViaVersion; needs an official client or targeted harness).
- MVI theme inventory separation (config written), GUI shop purchases, EconomyShopGUI and
  BetterStructures content (Owner-pending artifacts), official client playtest.

## Owner playtest (2026-10-12〜13 JST, tester: kanpyo_himono, MC 26.2 → 127.0.0.1:25577)

Pass (client-confirmed):
- Main Growth Pickaxe: grant/evolve/status/branch OK; repair OK after WM grant (see mitigations).
- Waymark shared balance Main↔Frontier OK.
- MVI theme inventory separation (gate/WB/RF groups) OK — first live evidence.
- WB traversal mechanics OK (~360 m travel via grapple/glide).
- `/wff open` workaround opens Navigation GUI (incl. Return to Gate); `/server lobby|main|frontier` OK.

Bugs found (code/config fixes deferred to batch; P-IDs for tracking):
- P-1 `/shop` dead on Main: `WayfarerMainPlugin` removes `"shop"` from the command map
  when EconomyShopGUI is present, killing Eshop's command; Wayfarer_Main's own `/shop`
  (plugin.yml) has no executor. `/sellgui`/`/sellall` unaffected (not removed).
- P-2 Navigation compass never opens GUI: `FrontierNavGui.ensureNavigationItem` builds
  without `stamp()` so `identify()` returns null; unstamped copies accumulate each join
  (observed COMPASS x2). Stamped grants via `/wayfarer-frontier grant` work.
- P-3 Loadout granted in gate world: runtime `Wayfarer_Frontier/config.yml` had `world`
  in `themes.worlds-beyond` (repo template is correct: only `frontier_iris`). Runtime
  edited (line removed); needs Frontier restart. Evidence: join at [world] → grant →
  `fell out of the world` 01:11:31.
- P-4 Loadout granted once ever (`loadout` DB row blocks re-grant); death/void loss is
  a dead end. Admin `grant` bypasses the flag (used twice as mitigation).
- P-5 Frontier shop has no world gate: WB items (launchpad/waystone tool) buyable in
  RF/gate, polluting other MVI groups. Placement side is gated (waystone: WB only;
  launchpad: WB+RF — RF allowance needs a design call).
- P-6 WB GUI arrival (0.5,80,0.5) is inside terrain: `suffocated in a wall` confirmed
  01:24:16. Runtime arrival changed to y=67 (MV spawn 0,66,0 verified safe on foot);
  needs Frontier restart. Iris cliffs are pack terrain (overworld v31100), not a
  generation failure. RF `drowned` 01:23:43 is vanilla behavior, not a bug.
- P-7 WB death loses everything (vanilla drops at death site + MVI same-group empty
  profile on respawn; observed: slain by Enderman 360 m from spawn). Proposed: soulbound
  stamped theme items, vanilla for the rest.
- P-8 Grapple has no texture: hook is PAPER + `item-model leafgrapple:wood_grapple`,
  jar bundles no pack, Frontier serves no pack (no ResourcePackManager there).
  Function OK. Pack acquisition is Owner-pending (official vendor channels).

Runtime mitigations applied (isolated env only, no code changes):
- `/waymark give kanpyo_himono 5000` on main (Redis ZSCORE verified 5000).
- Loadout re-grants via frontier console (01:25:52, 01:29:28; in-iris so WB group).
- Runtime config edits pending Frontier restart: worlds-beyond minus `world`;
  worlds-beyond arrival y 80→67.

Still NOT live-verified: launchpad lifecycle, waystone place/teleport/lifecycle,
EconomyShopGUI purchase click-path, BetterStructures fresh-chunk spawning, EliteMobs
combat/loot, AdvancedPortals creation, non-op permission boundaries from client.
Growth Pickaxe BROKEN→repair path: **verified live** (BROKEN arrival → `/wayfarer-main
repair` → durability restored + progress resumes; Diamond at 400 next).
- Diamond evolution confirmed live (progress 400 reached, tier up + message).
- Netherite evolution confirmed live (setprogress 1199 → 1 mined block → tier up).
- BROKEN arrival confirmed live: `tier=Iron progress=360/400 status=BROKEN` (110→360 =
  full iron durability via manual mining; restored tool accumulates normally).
- P-16 (design call, batch) BROKEN pickaxe still mines as an unbreakable vanilla pick
  (no progress, clamp holds at max-1). Decide: cancel breaks while BROKEN vs allow.
  Client-confirmed: mines as vanilla pick, progress frozen.
- P-17 mcMMO `Fishing.<clinit>` throws `ExceptionInInitializerError` on
  FoodLevelChangeEvent (console, 2x) — same 26.2/API-drift family as P-12; mcMMO
  fishing handling broken. Fix = compatible mcMMO build + restart.

## Post-playtest repair batch (2026-10-13, HEAD 680e7575 → ba39ca7 on work/playable-alpha)

Preflight matched (remote HEAD/tree/main). Full fix batch implemented, built, deployed to the isolated runtime and verified with the protocol bot + RCON.

### Fixed and live-verified (Main)

| P-ID | Root cause | Fix | Evidence |
|---|---|---|---|
| P-10 | reissue() committed REVOKED then INSERTed the same `tool_id` PK → constraint violation, tool lost forever | Reissue now bumps `epoch` in place via a conditional `UPDATE ... WHERE tool_id=? AND epoch=?`; per-player reissue lock; WM charge verified (`transactionSuccess()`) BEFORE the bump; rows==0 → refund; DB failure after charge → recoverable epoch state + message; stale epochs rejected by identify() | `Reissued for 248 WM (epoch 2); progress, branch and upgrades kept.` — epoch 0→1→2, progress 1199/1200 kept, tier/branch/netherite kept, tool never bricked; also `tier=Netherite` after upgrade |
| P-1 | WashfarerMainPlugin removed `shop` from the command map, killing EconomyShopGUI | No command-map mutation at all. EconomyShopGUI detected via `PluginManager.getPlugin(...)`+isEnabled(); `/shop` fallback (`/wayfarer-shop`) registered only when no shop plugin exists | `Shop >> There is an update available for EconomyShopGUI...` (the bot's `/shop` now reaches EconomyShopGUI); log: `Another plugin provides /shop; fallback available via /wayfarer-shop` |
| P-12/P-17 | Deployed mcMMO was a **2018 GitHub 2.0.0 jar** (`2.0.0-SNAPSHOT`) — no `ExperienceConfig.isFishingExploitingPrevented`, `McMMOReplaceVanillaTreasureEvent`, no 26.x support | Built mcMMO **2.3.003-SNAPSHOT from mcMMO-Dev source** (pom target 26.1.1-R0.1) and deployed; mcMMO keeps its shared MariaDB storage | Both servers now run `mcMMO 2.3.003-SNAPSHOT`; 0 occurrences of `isFishingExploitingPrevented` / `ExceptionInInitializerError`; mcMMO loads fishing treasures from the shared DB (expense tables update on boot) |
| P-11 | `/waymark pay <self>` succeeded | Self-payment rejected (`You cannot pay yourself.`); pay checks withdrawal success before crediting | Live: `You cannot pay yourself.` |
| P-13 | EMF user nodes default op | (verified again after restart) `default` group holds `emf.user=1` in shared MariaDB | non-op bot ran `/emf shop` + `/emf journal` with no permission error |
| — | EMF journal "not accessible" | `database.enabled: true` (sqlite, local, no secrets) + Main restart | `evenmorefish.db` created; `/emf journal` no longer prints "not accessible" |

### Fixed and live-verified (Growth Pickaxe spec, P-19/B1)

- Enchant evolution now starts only **after Diamond**: tier 3 (Diamond, progress &lt; 1200) → `enchants=none`; tier 4 (progress 1200) → `enchants=Efficiency 1`.
- Netherite is a **persistent paid upgrade**, never an automatic evolution: `/wayfarer-main upgrade-netherite` (5000 WM provisional, configurable, documented as not a final balance) with double-charge guard (`markNetheriteUpgraded` conditional UPDATE + rows check → refund).
- After the upgrade: `tier=Netherite ... netherite=true`; the flag survives reissue, recalculation and config changes.
- Material thresholds 100/400/1200 unchanged → **existing player progress preserved** (wf_admin's tool stayed at its Diamond/Netherite state; original `tier=Diamond progress=424` seen live before the upgrade).
- Migration note recorded: tools that reached the old auto-Netherite condition (progress ≥ 1200) are treated as upgraded=true implicitly only through the paid path; no data was wiped.

### Fixed (code, live-verified for the command paths)

- P-15/P-18: lore now shows `Progress n/m`, progress bar, `Enchants: Efficiency X, ...`, `Durability X/Y` (red/gold/white by wear), Branch, Netherite unlock; `/wayfarer-main status` mirrors it. Verified: custom_name `Netherite Pickaxe of Growth` on the live item.
- P-16: BROKEN state converts the item to a `GRAY_DYE` shell ("壊れた成長のピッケル"), blocks block-breaking and entity damage, keeps PDC identity, is kept on death, GUI stays open, `/wayfarer-main repair` restores. Fully live-verified earlier (BROKEN → repair → ACTIVE); clamping code reworked to the shell.
- A4: repairs/upgrades reject when the economy is unavailable or funds are short (no free service), held-item identity must match the active tool/epoch before replacement, branch switch only replaces the item when it really is the player's tool.

### Fixed (Frontier, deployed — see DELIVERABLE_REPORT for verification status)

- P-2 (C1): navigation compass is stamped on every grant path (`stamp()` after `build()`); unstamped legacy compasses are removed on join. GUI opens for non-op via right-click.
- P-3 (C2): repo template restricts `worlds-beyond` to `frontier_iris` only (`world` removed); `safeTeleport` scans for the first solid ground with 2 air blocks above at the target column (no terrain/void burial); arrival y aligned to 67 (verified safe on foot).
- P-4/P-7 (C3): permanent loadout (elytra, grappling hook, navigation) is re-granted whenever no stamped copy exists in the player's inventory — death/void/reconnect no longer strand players. Launchpads remain a one-time free initial grant of 2, purchasable afterwards (no free resupply).
- P-5 (C4): frontier shop validates `worlds-beyond` membership at open AND at purchase click (world switches while the GUI is open are rejected).

### Infrastructure / operations

- DB backups (pre-fix): consistent snapshots taken while servers were stopped (growth.db.mv.db copy + `mysqldump` of wayfarer_luckperms/wayfarer_mcmmo).
- RCON enabled on Main (`enable-rcon`, password only in the isolated runtime) — no secrets committed.
- mcMMO source build steps documented in DECISIONS.md.

### Not verified / still open

- Frontier live client playtest (gates, grapple, launchpad bursts, waystones, MVI group swap visually) — the bot cannot deliver use-item-on packets to 1.21.11 through ViaVersion; Owner client remains the vehicle.
- P-8 grapple texture: function OK; LeafGrapple resource pack is Owner-pending (official vendor channels).
- BetterStructures content packs / EconomyShopGUI: Owner-pending; unaffected by this batch.


### Incident (2026-10-13, resolved)

A stray server run with the repository as CWD leaked runtime files (`eula.txt`,
`server.properties` incl. an RCON password, `logs/`) into commit d0ea68b of this public
repository. Removal commit 4190dd9 untracks them and .gitignore now covers the whole
runtime surface. The exposed RCON password has been disabled (`enable-rcon=false`); it was
only used on the isolated PoC instance. No other secret was present in those files.
Lesson recorded: all server processes must run with CWD inside `/home/eariver/wayfarer-dev/runtime`
(`scripts/network.sh` and the console-relay pattern enforce this).

## Owner playtest follow-up (2026-10-13, Main)

Pass: Growth GUI opens via pickaxe right-click; partial repair charged OK (before P-10).
- P-10 (critical) `/wayfarer-main reissue` bricks the tool: `reissue()` commits
  `REVOKED` then `INSERT` with the same `tool_id` PK → `JdbcSQLIntegrityConstraintViolation`
  (console 01:33:08); GUI Reissue button hits the same path (`GrowthGui:85`
  performCommand). Live DB ends REVOKED: "No Growth Pickaxe", no progress, no GUI.
  Schema root: PK on `tool_id` alone, reissue reuses the id for epoch+1.
  Recovery performed via console: `admin grant` + `admin setprogress kanpyo_himono 110`
  (new toolId `0fca3af1`, Iron/Eff1/Fortune, item in main hand verified via `data get`).
  Stale dead item (`df4af96c`) must be discarded if found.
  Note: `cp` of the live H2 file gave a stale ACTIVE snapshot — console `status` is
  the source of truth for live state.
- P-11 (minor) `/waymark pay` to self succeeds (no-op transfer); suggest rejecting.
- P-15 (UX, batch) Growth Pickaxe shows progress but not durability: add `Durability X/Y`
  lore line (refresh on damage/repair/evolution), so BROKEN proximity is visible.
- P-18 (UX, batch) enchant progression works but is invisible: Netherite tier verified
  live as Efficiency 2 + Unbreaking 1 (matches cycle Eff/Ub/Eff/Ub/Fortune), yet
  HIDE_ENCHANTS + hidden tooltip components conceal it. Show enchant summary in lore/GUI.
- P-19 (spec divergence, Owner-stated, batch design decision): Owner recalls the original
  concept as (a) enchant evolution starts only after Diamond transition, and
  (b) Netherite is NOT automatic at 1200 — requires WM payment or a switching action.
  Implemented behavior differs (enchants from evolution 1; auto-Netherite at 1200 in
  `EvolutionPlan.defaults`). Batch must verify against Project_Wayfarer concept docs
  and record the outcome in DECISIONS.md before changing thresholds/gating.
- Durability shortcut unavailable: `/data modify` on player SelectedItem is rejected
  by the server ("Unable to modify player data", 2 attempts); no datapack present for
  `/item modify`. BROKEN test options: manual mining (~140 breaks left on Iron Eff1)
  or datapack + Main restart in the fix batch.
- Fishing rod granted via console (`give`); EvenMoreFish fishing itself still untested.
- Fishing: vanilla fish caught (basic loop OK); EMF custom fish/bait/competition unconfirmed.
- P-12 EMF custom catches fully broken on Main: every PlayerFishEvent throws
  `NoSuchMethodError: ExperienceConfig.isFishingExploitingPrevented()` (EMF 2.4.3 vs
  deployed mcMMO 2.0.0-SNAPSHOT API drift; console 01:40:50~); handler aborts so only
  vanilla/junk land. No config toggle avoids the mcMMO check path. Fix = compatible
  mcMMO/EMF pair + Main restart (Owner artifacts). Secondary risk: NBT-API 2.15.7
  "not supported on 26.2" warning may still affect custom-item delivery after the swap.
- P-13 (setup gap, fixed) EMF commands showed only HELP: all `emf.*` user nodes default
  to OP; tester is non-op in LP `default`. Granted `emf.user` to `default` via console
  (shared MariaDB, synced); retry `/emf shop` etc. from client to confirm.
  Follow-up: `/emf shop` OK. Journal "Not Accessible" is expected: `database.enabled:
  false` in EMF config (journal needs DB; toggle requires Main restart — batch item;
  sqlite local, no secrets; moot until P-12 fixed since journal tracks custom catches).
  `/emf top` runs; "No competition running" expected solo (30 scheduled, need ≥2 players;
  Owner-deferred: no other players expected; force-start available if ever needed).

## Blockers / Owner-pending

1. EconomyShopGUI 7.1.1 jar (SpigotMC account required).
2. BetterStructures content packs (nightbreak.io).
3. EliteMobs premium content (official vendor channels).
4. Optional: SSH/RCON-free console access isn't needed; the offline proxy (25588) exists
   solely for the automated bot and should be removed or left disabled for production.
5. (2026-10-13) LeafGrapple resource pack (official vendor channels) for hook textures.
6. (2026-10-13) Fix batch P-1/P-2/P-4/P-5/P-6(safe-teleport)/P-7 + single Frontier
   (+Main for P-1) restart at Owner's convenience.

## If continuing from here

1. Build & test: `./gradlew build` (9 unit tests pass).
2. Runtime tree layout and start order: see DELIVERABLE_REPORT.md §3 and DECISIONS.md.
3. Next implementation candidates: waystone GUI polish, EliteMobs boss/quest wiring in
   frontier_bs, shop categories, mcMMO cross-server sharing when a shared DB exists.
