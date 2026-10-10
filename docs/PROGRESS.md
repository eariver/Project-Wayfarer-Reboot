# PROGRESS — Playable Alpha

**Branch:** `work/playable-alpha`
**Last updated:** 2026-10-11 JST
**HEAD:** (see git log)

## Reference investigation (done)

- Project_Wayfarer @ `344eedc` (docs v0.0.6, 2026-07-25): plugin-manifest.yml, versions.yml, frontier lock 003, concepts/frontier specs.
- Project-Wayfarer-Plugins @ `efe9d81` (main, V0.0.1): Core = MariaDB/Redis/Hikari/Flyway infra + Waymark transaction manager. Growth Pickaxe / frontier gameplay exist only on frozen legacy PR #14 branch `0f06ee9` and PR #18 redesign docs. No stable published release binaries.
- Project_Wayfarer_Research_Data: not required for this PoC.

## Environment decisions (see docs/DECISIONS.md)

- Isolated runtime at `/home/eariver/wayfarer-dev/runtime` (outside this repo; contains worlds/player data — never committed).
- Network: Velocity 4.1.0-SNAPSHOT build 8 (git-9eb338bd-b8, matches manifest) + Paper 26.2 build 133 (manifest recorded build 62; older build not served by fill API) + Paper 1.21.11 build 132 (exact production build, commit c5eb079).
- Java: system OpenJDK 25.0.4.1 (owner installed); extra Temurin JDK 21.0.12.1 at `~/.jdks/` for Iris source build.
- All plugin jars downloaded from official sources and SHA-256 verified against versions.yml where the manifest records one (Modrinth/Hangar/GitHub). Vault & EconomyShopGUI: SpigotMC requires account → **not obtainable**; VaultUnlocked 2.20.2 provides the Vault API bridge; shop to be covered by a Wayfarer in-repo GUI; EconomyShopGUI listed as Owner-pending artifact.
- RedisEconomy requires Redis server (no redis-server/docker/root on this box) → shared Waymark economy will be provided by a Wayfarer in-repo provider backed by shared SQLite (documented substitution).

## Current status

| Item | State |
|---|---|
| Runtime skeleton (velocity + 3 paper backends, ports 25577/25578/25579/25580) | DONE |
| Core binaries + SHA-verified plugin set downloaded | DONE |
| Lobby server: void default world, 17x17 platform + gold center, spawn 0/64/0, gamerules | DONE |
| Main server: vanilla default world boot-verified on Paper 26.2, all production plugins enabled | DONE |
| Frontier server: Paper 1.21.11, EliteMobs/LeafGrapple/MVI/mcMMO/TAB/VaultUnlocked all enabled | DONE |
| Iris 3.9.2 engine (source-built, GPL-3.0, size matches manifest ±1B) + IrisDimensions Overworld pack installed | INSTALLED (world creation pending) |
| Velocity boot with ViaVersion/LuckPerms/TAB/VelocityScoreboardAPI | PENDING |
| mcMMO on Paper 26.2 | PARTIAL: loads; Alchemy potion warnings (MC26 potion renames); EvenMoreFish↔mcMMO treasure event registration fails (mcMMO 2.0.0 API drift) — documented degradation |
| Wayfarer custom plugins (growth pickaxe / frontier traversal / waymark provider) | NOT STARTED |
| Automated protocol-level test client | PLANNED (needed for real runtime evidence) |
| Networks gates / inventory separation verification | NOT STARTED |

## Next

1. Boot velocity + verify proxy→backend forwarding (offline test velocity on 25588 with same secret for the automated client).
2. Create frontier_worlds: frontier_gate (void default), frontier_iris (Iris:overworld pack), frontier_bs (vanilla, EliteMobs).
3. Gradle project in this repo: `wayfarer-main` (Growth Pickaxe), `wayfarer-frontier` (traversal/waystones/gates), `waymark-provider` (shared SQLite economy, Vault).
4. Vertical slice: Growth Pickaxe on main → verify with test client (mine → progress → evolution → reconnect persistence).
5. Frontier loop: gate portal, launchpad/elytra/grapple, EliteMobs combat + loot.
6. Cross-theme: inventory separation via MVI, shared Waymark, permission boundary checks.
7. Report + PR.

## Blockers / Owner-pending

- EconomyShopGUI 7.1.1 jar (SpigotMC account needed).
- BetterStructures content packs (nightbreak.io account/paid packs) — PoC will run without free-packs or with substitutes.
- Production MariaDB/Redis shared storage not available in sandbox → mcMMO shared progression and cross-server currency use PoC-local substitutes.
