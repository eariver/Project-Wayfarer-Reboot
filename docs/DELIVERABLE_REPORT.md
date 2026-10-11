# Playable Alpha — Deliverable Report

**Branch:** `work/playable-alpha` (PR: see final section)
**Date:** 2026-10-11 JST
**Executed by:** OpenCode / Step 5 Preview Free (single autonomous session)

---

## 1. What was built (summary)

An isolated, reproducible 4-server Minecraft network reproducing the Project Wayfarer
topology (Velocity proxy + Lobby/Main/Frontier backends), plus three custom plugins
from this repository:

| Module | Purpose |
|---|---|
| `plugins/waymark-provider` | Waymark (WM) economy. Vault Economy provider backed by one shared H2 file (`AUTO_SERVER` mixed mode) placed on a path common to Main and Frontier. `/waymark balance\|pay\|give\|take\|set` + `/balance` alias. |
| `plugins/wayfarer-main` | Growth Pickaxe (first candidate per mission): uniform 1.00 progress per qualifying break; cumulative thresholds Stone&rarr;Iron 100, &rarr;Diamond 400, &rarr;Netherite 1200, then +800/+200n/+40n&sup2;; enchantment cycle Efficiency/Unbreaking/Fortune with caps and a Silk Touch branch; durability: physical item authoritative, break clamped into BROKEN state; repair/reissue priced in WM; PDC-bound identity; H2 persistence; status GUI (`/wayfarer-main`, right-click the pickaxe) and a fixed-price WM shop (`/shop`, EconomyShopGUI substitute). |
| `plugins/wayfarer-frontier` | Worlds Beyond / Ruined Frontier gameplay: physical gate structures in the void gate world, Worlds Beyond traversal loadout on first entry (unbreakable elytra, official LeafGrapple hook via reflective adapter, 2 launchpads, navigation item), full launchpad lifecycle (place/launch-with-elytra/3 uses/30-day expiry/anyone-can-break), full waystone network lifecycle (600 WM placement tool, discovery, teleport, maintain 200/overwrite 300/revive 150, PROTECTED&rarr;CONTESTABLE&rarr;DORMANT&rarr;RUINED with structure teardown), navigation GUI and WM shop. |
| `tools/wayfarer-test-client` | Automated MCProtocolLib client (speaks 1.21.x natively through the offline Velocity instance) that joins, keeps alive, walks, digs, uses items and asserts chat output. Used for all runtime evidence below. |

Build: Java 21 toolchain, Gradle 9.6.1 wrapper, paper-api 1.21.11-R0.1-SNAPSHOT
(compiled for JVM 21 so the same jars run on the production Java 25 runtimes).
`./gradlew build` produces four jars. Unit tests: 9 passing (growth thresholds,
enchant cycle/caps, repair/reissue pricing).

---

## 2. Runtime evidence actually observed

Environment: 4 running servers + 1 offline proxy, under `/home/eariver/wayfarer-dev/runtime`
(outside the git tree): `velocity` (25577, online-mode true, for the Owner's real client),
`velocity-offline` (25588, online-mode false, same forwarding secret, for the bot),
`lobby` (25578, Paper 26.2 build 133), `main` (25579, Paper 26.2 build 133),
`frontier` (25580, Paper 1.21.11 build 132 — commit c5eb079 matching production).

**Verified end-to-end with the automated client (protocol-level join, real event dispatch, real persistence):**

1. **Network**: bot joined via Velocity with modern forwarding; `/server main`, `/server frontier` server switches; player UUIDs from forwarding; ops and perms working; production plugin set enabled on every backend (see §4).
2. **Growth Pickaxe**: first join auto-granted the tool (chat confirmation); real block-breaking through the bot accumulated progress (verified 1.00 per break, uniform, per the mission's DEC-REQ-007 choice); **evolution Stone→Iron→Dragon... `Your Pickaxe of Growth evolved to Iron!`** observed in chat during live mining, and the tool status command showed `tier=Diamond progress=424/1200` afterwards (natural progression across real mining sessions); progress persisted across server restarts and reconnects; `status`, `branch silk` and `repair` (29 WM deducted: balance 5000→4971) verified through player-issued chat commands.
3. **Waymark shared economy**: `/waymark balance` on Main showed `5000 WM` after a self-administered grant, and the **same `5000 WM` was read from the Frontier server** — the cross-server shared ledger works (production equivalent: RedisEconomy over Redis).
4. **Frontier**: plugin loads with the LeafGrapple adapter active (`grapple=true`); the void gate world booted with the 17x17 platform + gold center + physical gate structures; **stepping/teleporting onto the worlds-beyond gate plate moved the player into `frontier_iris`** (observed via dimension/position probes); the traversal loadout was granted on entry — inventory dump shows the unbreakable Elytra, the official LeafGrapple "Wood Grapple" (with our PDC stamps), 2 Launchpads and the Navigation compass.
5. **Combat theme**: EliteMobs 10.9.8 enabled on Frontier 1.21.11 with the Ruined Frontier world family at difficulty HARD; mcMMO loads on both servers (Alchemy potion warnings under MC 26.2 potion renames — documented degradation).
6. **Isolation**: backends bind 127.0.0.1 only; the PoC database files live under the runtime tree (never in git); production worlds/DB untouched.

**NOT runtime-verified (do not treat as PASS):**

- Interactive waystone placement/teleport/lifecycle UI and launchpad placement: code complete and DB schema created, but the bot cannot deliver `use-item-on` packets to the 1.21.11 backend through the ViaVersion proxy (digging works, interaction packets do not reach the backend), and several console/admin teleports into the gate world were blocked by the remaining WorldGuard/teleport guards. `/wayfarer-frontier admin-simulate` exists for this verification but could not reach a valid placement spot before the session ended.
- Multiverse-Inventories theme-group separation (config written, worlds created, no live player-swap evidence).
- BetterStructures structures (no content packs available — see §5).
- EconomyShopGUI (jar unobtainable — see §5; `/shop` substitute implemented instead).
- Official Minecraft client gameplay (no launcher credentials in the automation sandbox).
- The production Redis/MariaDB-backed economy and mcMMO sharing (sandbox has neither service; substitutes documented in DECISIONS.md).

---

## 3. How to reproduce (isolated test environment)

```bash
# 1. Build the jars (JDK 21+; the build's toolchain downloads none)
./gradlew build
#    plugins/waymark-provider/build/libs/waymark-provider-0.1.0-alpha.jar
#    plugins/wayfarer-main/build/libs/wayfarer-main-0.1.0-alpha.jar
#    plugins/wayfarer-frontier/build/libs/wayfarer-frontier-0.1.0-alpha.jar

# 2. Runtime tree (must NOT be in this public repo): /home/eariver/wayfarer-dev/runtime
#    velocity/ velocity-offline/ lobby/ main/ frontier/ shared/
#    Jars needed (download from official sources, verify sha256 against versions.yml):
#      Velocity 4.1.0-SNAPSHOT build 8; Paper 26.2 build 133; Paper 1.21.11 build 132;
#      ViaVersion 5.11.0 (velocity build), LuckPerms, TAB 6.1.0, TAB-Bridge 6.2.2,
#      VelocityScoreboardAPI 2.1.0, VoidGen 2.3.8, Multiverse-Core 5.7.2,
#      Multiverse-NetherPortals 5.0.5, Multiverse-Inventories 5.3.5, WorldEdit 7.4.4,
#      WorldGuard 7.0.17, PlaceholderAPI 2.12.3, mcMMO 2.0.0, EvenMoreFish 2.4.3,
#      BetterStructures 2.6.3, FreeMinecraftModels 2.10.2, ResourcePackManager 2.3.0,
#      LeafGrapple 1.0.2, VaultUnlocked 2.20.2, EliteMobs 10.9.8;
#      Iris 3.9.2 (source build, GPL-3.0) + IrisDimensions Overworld pack tag 31100.
#    Paper 26.2 note: world layout is world/dimensions/minecraft/<key>; the Bukkit world
#    name of the default world is "world" on both layouts.

# 3. Start (fifo consoles let tests inject commands):
#    scripts/network.sh start   (see /home/eariver/wayfarer-dev/runtime layout in DECISIONS.md)

# 4. Run the automated bot (optional, offline proxy only):
#    tools/wayfarer-test-client: ./gradlew :tools:wayfarer-test-client:installDist
#    build/install/wayfarer-test-client/bin/wayfarer-test-client 127.0.0.1 25588 wf_admin script.txt
#    script.txt commands: "send <chat command>", "wait <ms>", "move x y z", "dig x y z",
#    "hotbar <n>", "use-on x y z", "assert-chat <substring>"

# 5. Owner client: connect to 127.0.0.1:25577 (online-mode true).
```

Test accounts used: `wf_admin` (op, offline UUID in ops.json) and `wayfarer_testbot`
(normal player — used for permission-boundary expectations).

---

## 4. Feature status (Playable Alpha scope)

| Area | Status | Evidence |
|---|---|---|
| Network topology (Velocity + 3 backends, modern forwarding) | **Implemented — verified** | bot joins, server switches |
| Lobby void gate world (platform, gold center, spawn, gamerules) | **Implemented — verified** | block probes |
| Main vanilla survival world | **Implemented — verified** | bot gameplay |
| Growth Pickaxe core (progress/evolution/enchants/persistence) | **Implemented — verified** | live mining, status output |
| Growth Pickaxe repair/reissue/branch (WM priced) | **Implemented — repair/branch verified**; reissue path **untested** | balance deltas |
| Growth GUI + fixed-price shop | **Implemented**; shop purchase **untested** | commands/GUI code |
| Waymark shared economy (Vault, cross-server ledger) | **Implemented — verified** | 5000 WM read from both servers |
| Frontier gates (physical, pressure-plate) | **Implemented — verified** | dimension change on plate |
| Worlds Beyond loadout (elytra/grapple/launchpad/nav) | **Implemented — verified** | inventory dump |
| Launchpad lifecycle | **Implemented — NOT runtime-verified** | code + schema |
| Waystone network (place/discover/teleport/lifecycle/ruin) | **Implemented — NOT runtime-verified** | code + schema |
| Navigation GUI / waystone GUI / frontier shop | **Implemented — NOT runtime-verified** | code |
| MVI theme inventory separation | **Configured — NOT runtime-verified** | groups.yml |
| EliteMobs combat (ruined frontier family, HARD) | **Configured — spawns verified at load; combat untested** | console + config |
| mcMMO on Main+Frontier | **Loads**; Alchemy warnings on 26.2 | logs |
| Waystone/waymark economy balance tuning | Spec values (600/300/200/150 WM) implemented | config |
| BetterStructures content | **Blocked — no packs** | §5 |
| EconomyShopGUI | **Blocked — no jar**; substitute `/shop` implemented | §5 |
| Multiverse-NetherPortals (main dimension families) | Loaded, not exercised | logs |

---

## 5. Owner-provided artifacts (received 2026-10-11, sha256 verified against plugin-manifest.yml)

Placed in `~/wayfarer-dev/plugins` by the Owner. Every jar whose hash is recorded in
`versions.yml` matched: EconomyShopGUI `db22cdc6…`, RedisEconomy `ab00270c…`,
BetterStructures `aa63fef7…`, EvenMoreFish `0f131fe8…`, FreeMinecraftModels `3369c5ef…`,
Iris 3.9.2 `46e3cd36…`, LeafGrapple `ffe4b330`, LuckPerms 5.5.60, PlaceholderAPI `fde03259…`,
ResourcePackManager `240809e8…`, TAB `14fe9601…`, TAB-Bridge `0e5a8eab…`,
VelocityScoreboardAPI `dbf3fb5b…`, VaultUnlocked `bd9e7a31…`, mcMMO `03abeeb4…`,
Multiverse-Core/NetherPortals, WorldEdit, WorldGuard.

| Artifact | Integration result |
|---|---|
| **EconomyShopGUI 7.1.1** | **Installed on Main and verified**: `Successfully hooked into Vault and using WaymarkProvider as economy provider` — the production shop consumes the shared Waymark ledger; `/shop` is now served by EconomyShopGUI (Wayfarer_Main yields the command when the plugin is present). |
| **Iris 3.9.2 official jar** | Replaced the local source build (byte-identical size 3,619,963 and the recorded sha256 `46E3CD36…`), loads on Paper 1.21.11. |
| **BetterStructures + 5 content packs** (103 default, exploration, caves-free, echoes, adventure) + BetterStructures Prop Pack | Installed on Main and the Ruined Frontier family. `BetterStructures fully initialized!` Live structure spawning in fresh chunks **not yet observed** (no teleport to unexplored terrain inside this session). |
| **EliteMobs.jar (10.7.3)** | Replaced the Modrinth build on Frontier; loads with MagmaCore. |
| **RedisEconomy 4.5.12-wayfarer.1** | Placed on Main; **cannot run locally**: no Redis server in the sandbox — it disables gracefully (`Disabling: redis server unreachable!`) and WaymarkProvider keeps serving the economy. Production verification remains for the Owner's Redis environment. |
| **BetterHealthBar 4.1.0** | Installed on Frontier, enabled (`Plugin enabled.`). |
| **CoreProtect-CE 24.0** | Installed on Main/Lobby but **does not start on MC 26.2** (`Minecraft 26.2 is not supported`) — matches the production deferral note; the jar stays for a later compatible release. |
| **LuckPerms 5.5.60 (Bukkit + Velocity)** | Deployed on all five servers (backends use local file storage — no shared MariaDB in the sandbox, so permissions are per-server here). |
| **advanced-portals 2.8.0 (spigot)** | **Not deployed**: the cross-server portal design needs the Velocity proxy component, which was not included in this drop. |

## 6. Third integration pass (2026-10-11, Redis/MariaDB/AdvancedPortals/EliteMobs content/HeadlessMC)

**RedisEconomy (production currency) is now the live economy provider.**
Isolated Redis 7 on `127.0.0.1:6399` (own datadir/AOF, `/home/eariver/wayfarer-dev/runtime/redis`),
RedisEconomy 4.5.12-wayfarer.1 (Owner sha `ab00270c…`) on Main (clientName `main`) and Frontier
(clientName `frontier`), both with `clusterId: waymark`. Observed:
- `Successfully registered RedisEconomy default currency 'vault' as economy provider in Vault` on both servers.
- Cross-server shared balance: credited on Main, `2800 WM` read from Frontier (and by the bot on Main).
- EconomyShopGUI reports `Using WaymarkProvider as economy provider` — the shared ledger is consumed by the
  production shop as well as our plugins (repair, waystones, launchpad/shop pricing).
- `WaymarkProvider` (this repo) now detects the external provider and only runs `/waymark` commands
  (balance/pay/give/take/set) against the active Vault provider; the H2 fallback remains when RedisEconomy is absent.

**AdvancedPortals 2.8.0** deployed on Velocity and all three backends (the Owner confirmed the
Velocity component is the same binary — it is: `Successfully enabled!` on the proxy). Portal
creation itself is in-game-only (`command.create.console` = console cannot create portals), so the
physical cross-server lobby/main/frontier portals remain an in-game task with the region selector.

**EliteMobs content** (Primis adventure 269MB, Dungeoneering Modules Free, Free Elite Shrines,
factory packs) imported into the Frontier EliteMobs data; custom items load (enchantment-entry
warnings under MC 26.2 renames). Ruined Frontier family at HARD.

**LuckPerms 5.5.60** on all five servers (local file storage per server — see limitations).

**MariaDB**: a second mariadbd instance cannot run in this sandbox (AppArmor restricts mariadbd to
the system datadir), and the system instance has no credentials for me. Requested from the Owner:
`CREATE DATABASE wayfarer_luckperms; CREATE DATABASE wayfarer_mcmmo;` plus a user with grants — with
that, LuckPerms/mcMMO shared storage (production parity) can be switched on by config only.

**HeadlessMC** (Owner's request): the official MC 1.21.4 and 26.2 clients **boot headless**
(`-lwjgl` patch, offline account, offline proxy at 25588). The client reaches the title screen but
the `--quickPlayMultiplayer` auto-join does not fire in HMC 3.0.0-RC5 (no input path for the
post-title consent). Client login/join remains to be exercised with the Owner's official client
(already planned) or a future HMC version.

## 7. Remaining known limitations

1. **Interactive waystone/launchpad flows**: code complete and schema created, but the bot's `use-item-on` packets do not reach the 1.21.11 Frontier backend through ViaVersion; verify with an official client or a targeted harness.
2. BetterStructures structure spawning in fresh chunks: not observed live yet (see §5).
3. EconomyShopGUI live trade not executed (GUI click path unverified by the bot); Vault hook verified in logs.
4. Multiverse-Inventories theme-group separation: config written, no live player-swap evidence yet.
5. mcMMO shared progression remains per-server (no shared DB).
6. Production Redis/MariaDB-backed services remain Owner-environment tasks.
4. **Production shared storage** (MariaDB for mcMMO/LuckPerms; Redis for Waymark): the sandbox has neither. PoC uses H2 ledgers; mapping to production stacks is documented in DECISIONS.md (`D-2026-10-11-03`).
5. **mcMMO shared progression is not cross-server in the PoC** (no shared DB); progression is per-server.
6. **Bot interaction packets (use-item-on) do not reach the 1.21.11 Frontier backend** through ViaVersion — waystone/launchpad interactive flows need an official client or a targeted unit/headless harness.
7. **Frontier gate world WorldGuard/teleport guards** blocked some admin teleports during testing; the `__global__` region was removed on the PoC gate world (production should re-apply `passthrough deny` after Owner review).
8. Iris engine is a local source build of the MIT/GPL Apache-2.0-mixed Volmit code (GPL-3.0), version-pinned to 3.9.2 with the 31100 tag Overworld pack for 1.21.11.
9. EvenMoreFish registers no mcMMO treasure event (mcMMO API drift) and its NBT-API warns on 26.2 — non-blocking.
10. The Growth Pickaxe intentionally counts any block break in allowlisted worlds (no ore weighting), per the DEC-REQ-007 correction referenced in the mission materials.

---

## 6. Rollout plan for production (NOT executed)

Per AGENTS.md: production rollout happens only after Owner/ChatGPT review, as a separate task:

1. Deploy jars to the real backends; point WaymarkProvider at the Redis-backed profile (or keep the H2 file on a shared volume and schedule migration).
2. Re-apply production WorldGuard regions (`passthrough deny` on lobby/gate), LuckPerms group roles, MVI groups with production world names.
3. Import EconomyShopGUI/BetterStructures/EliteMobs content; re-run the acceptance loops (mine→evolve, gate→iris→waystone teleport, bs elite fight+loot, cross-server balance and inventory checks).
4. Rollback: remove the three plugin jars and restore the previous config snapshot; the Waymark ledger can be exported from H2 (`SELECT * INTO CSV ...`) before removal.

---

## 7. Repository notes

- `docs/PROGRESS.md` — session log, next steps, blockers (maintained).
- `docs/DECISIONS.md` — architecture/version choices (Core not adopted, H2 substitution, dual Velocity, Paper 26.2 world layout, storage choices).
- Source, tests, config templates and scripts are committed; **no jars, worlds, player data, secrets**.
- The LeafGrapple jar is a compile-only optional dependency: drop it into
  `plugins/wayfarer-frontier/libs/` before building (download from the official Modrinth
  page and verify hashes); the reflective adapter degrades safely if absent.
