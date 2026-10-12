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
- BROKEN arrival confirmed live: `tier=Iron progress=360/400 status=BROKEN` (110→360 =
  full iron durability via manual mining; restored tool accumulates normally).
- P-16 (design call, batch) BROKEN pickaxe still mines as an unbreakable vanilla pick
  (no progress, clamp holds at max-1). Decide: cancel breaks while BROKEN vs allow.
  Client-confirmed: mines as vanilla pick, progress frozen.
- P-17 mcMMO `Fishing.<clinit>` throws `ExceptionInInitializerError` on
  FoodLevelChangeEvent (console, 2x) — same 26.2/API-drift family as P-12; mcMMO
  fishing handling broken. Fix = compatible mcMMO build + restart.

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
