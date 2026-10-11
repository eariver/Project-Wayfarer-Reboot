# PROGRESS — Playable Alpha

**Branch:** `work/playable-alpha`
**Last updated:** 2026-10-11 (final session update)
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
  MariaDB: a second instance cannot run (AppArmor) — Owner DB creation requested for shared
  LuckPerms/mcMMO storage. CoreProtect-CE 24.0 cannot start on MC 26.2 (upstream).

Remaining verification work (documented as NOT verified):
- Waystone/launchpad interactive flows (bot use-item-on packets don't reach the 1.21.11
  backend through ViaVersion; needs an official client or targeted harness).
- MVI theme inventory separation (config written), GUI shop purchases, EconomyShopGUI and
  BetterStructures content (Owner-pending artifacts), official client playtest.

## Blockers / Owner-pending

1. EconomyShopGUI 7.1.1 jar (SpigotMC account required).
2. BetterStructures content packs (nightbreak.io).
3. EliteMobs premium content (official vendor channels).
4. Optional: SSH/RCON-free console access isn't needed; the offline proxy (25588) exists
   solely for the automated bot and should be removed or left disabled for production.

## If continuing from here

1. Build & test: `./gradlew build` (9 unit tests pass).
2. Runtime tree layout and start order: see DELIVERABLE_REPORT.md §3 and DECISIONS.md.
3. Next implementation candidates: waystone GUI polish, EliteMobs boss/quest wiring in
   frontier_bs, shop categories, mcMMO cross-server sharing when a shared DB exists.
