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

The offending files remain reachable in commit d0ea68b of the pushed branch history (force-push is out of
scope per the mission boundaries), so the RCON password exposed there is considered compromised: RCON has been
**disabled** on the isolated instance (`enable-rcon=false`, `rcon.password=` cleared) and verification now uses the
console FIFO (`console.fifo`) instead. The password was only ever used on this PoC instance and was never shared
with any production environment.

## M1-M3 corrective maintenance batch (2026-10-13, HEAD 6121a14 → 4e2f9c4 (final) on work/playable-alpha)

Small corrective batch ahead of the independent review: git tracking repair, paid
growth-pickaxe failure paths, Waymark transfer integrity. Unit tests: 50 passed / 0 failed
(`./gradlew build` BUILD SUCCESSFUL).

### M1 — `.gitignore` over-exclusion

- Root cause: the broad `plugins/` rule made every NEW file under the real source modules
  (`plugins/wayfarer-main`, `plugins/wayfarer-frontier`, `plugins/waymark-provider`)
  invisible to git; existing tracked files stayed tracked so the gap only bit new work.
- Fix: replaced the broad rule with `plugins/build/`, `plugins/*/build/`, `plugins/*/libs/`,
  `plugins/*/src/main/kotlin/`, `tools/*/build/` (build outputs only) and dropped the
  duplicated `wayfarer-evidence/` entry. Security rules for runtime artifacts were kept
  (eula.txt, server.properties, logs/, cache/, world*/, crash-reports/, libraries/,
  versions/, commands.in, console.log, wayfarer-evidence/).
- Verified with `git check-ignore -v`: a new java source under the pay package is
  trackable; server.properties / logs / world data / JARs stay ignored. The added test and
  main sources of this batch are the live proof (they are tracked).

### M2 — paid growth-pickaxe operations (reissue / upgrade-netherite / repair)

- Root cause: the wallet and H2 were treated as if they were one transaction. `reissue`
  refunded unconditionally after a `findByToolId` miss even when the epoch bump had
  committed (free reissue), and the refund's own `EconomyResponse` was never checked; a
  throwing `bumpEpoch`/`markNetheriteUpgraded` escaped uncaught, leaving the player charged
  with no message and no record. `upgradeNetherite` had no operation lock.
- Fix: new `io.github.eariver.wayfarar.main.pay.GrowthPaidOps` (+ `VaultWallet`), used by
  `GrowthCommand` for reissue / upgrade-netherite / repair. It classifies every outcome
  through a re-read of the authoritative row: applied (deliver, rejoin recovers),
  not-applied-0-rows (compensating refund), not-applied-probe, unknown (charge kept,
  reference surfaced). Refunds are confirmed before the player is told they were not
  charged; unknown/refund-failed states log and show a reconcile reference.
  `GrowthRepository` now implements `GrowthPaidOps.ToolStore`; the same reissue lock also
  guards the Netherite upgrade so double clicks cannot double-apply.
- Tests: `GrowthPaidOpsTest` 13 cases (happy paths, declined, unconfirmed withdraw,
  0-row refund, refund failure, mutation-throws with row unchanged/applied/unreadable/
  vanished, delivery failure recovery, duplicate netherite upgrade, repair variants).

### M3 — Waymark transfers

- Root cause: `/waymark pay` called `withdrawPlayer`/`depositPlayer` and ignored both
  `EconomyResponse`s; the local fallback ran withdraw and deposit as two separate
  transactions; `give/take/set` had unchecked return values (`set` always yielded true);
  and `WaymarkLedger`'s shared `MERGE` upsert reset an existing holder's balance to 0 on
  every deposit (currency loss).
- Fix: `WaymarkLedger` extracted from the plugin with a single-transaction `transfer`
  (guarded debit + credit + two ledger rows, all-or-nothing) and a non-destructive
  `ensureRow`; `WaymarkTransfer` (+ `WaymarkProviderPlugin.VaultAdapter`) drives the
  external path with verified legs, compensating refund on deposit failure and explicit
  statuses. The command maps them to messages and only reports success on a verified send.
- Documented residual constraints: no exactly-once across provider legs (a crash between
  the legs can leave the sender debited — the reference string is the reconcile handle),
  and the compensating withdrawal may touch the recipient's own coins if a deposit partly
  landed.
- Tests: `WaymarkTransferTest` 6 cases (normal, self, invalid amount, missing provider,
  insufficient funds, withdraw unconfirmed, deposit-refunded, deposit-refund-failed,
  admin give/take/set) + `WaymarkLedgerTest` 6 cases (both sides, insufficient, self,
  rollback-without-currency-loss via a CHECK-constrained ledger, guarded withdraw,
  negative set rejected).

### Runtime verification (isolated PoC, console FIFO; RCON stays disabled)

- main + frontier restarted on the new jars; startup logs verified
  (`Wayfarer_Main ready`, mcMMO 2.3.003-SNAPSHOT, `/shop` delegation, WaymarkProvider
  delegating to Vault); 0 mcMMO API exceptions.
- `/waymark balance` → 0 WM; self-pay → "You cannot pay yourself." (P-11 kept);
  `/waymark pay kanpyo_himmono 1` → "Payment failed: insufficient funds";
  reissue after funding: "Reissued for 216 WM (epoch 1); progress, branch and upgrades
  kept." with the balance moving 500 → 284 (single charge, epoch 0 → 1, progress/branch/
  netherite preserved). Growth pickaxe data intact after the restart.

### Not verified here

- Interactive UI flows (GUI, nav items, waystones/launchpads) still need the Owner client.
- Frontier live gameplay unchanged (jar not modified this batch).

## R-01..R-15 residual audit & corrective batch (2026-10-14, HEAD 0e71d8a)

Independent code review findings fixed on `work/playable-alpha` plus an extra audit pass.
Unit tests: **90 passed / 0 failed** (`./gradlew build` BUILD SUCCESSFUL).

### Priority 0 — Data integrity / currency / security

| ID | Sev | Status | Root cause | Fix | Tests |
|---|---|---|---|---|---|
| R-01 | Critical | FIXED | after a refused deposit the code called `provider.withdraw(to, amount)` — it debited the RECIPIENT instead of refunding the sender (asset integrity bug); the old test asserted this wrong behaviour | `WaymarkTransfer` now compensates the SENDER (`deposit(from, …)`) and only when the credit was certainly refused; an unknown credit outcome performs no compensation at all | `WaymarkTransferTest` 13 cases incl. the A=1000/B=500 → 250 transfer with total conservation asserts |
| R-02 | Critical | FIXED | `Db.tx()` called `connection()` outside its try, so a connection failure leaked the ReentrantLock; and a nested `tx()` COMMITTED the enclosing transaction (WaymarkLedger read the public `balance()` inside its tx) | `Db` keeps a per-thread depth: only the OUTERMOST frame owns autoCommit/commit/rollback; `connection()` moved inside the try; WaymarkLedger uses the connection-aware balance | `DbTransactionTest` 4 cases (nested commit, nested rollback, depth flag, lock release on a file-as-parent path) |
| R-03 | Critical | FIXED | `registerBreak` added 1 to the last-persisted cached progress, so N breaks between checkpoints overwrote each other (only +1 counted) | new `ProgressBuffer` domain overlay + `accumulateBreak()`/`latestProgress()`/`viewOf()` in the plugin; failed checkpoints keep the pending value; quit/evolution/BROKEN transitions preserve it | `ProgressBufferTest` 7 cases incl. 100 consecutive breaks = +100 |
| R-04 | High | FIXED | provider exceptions were indistinguishable from a refusal, so a throwing leg was reported as a clean failure/“not charged” | tri-state `Confirmation{OK,FAILED,UNKNOWN}` in `GrowthPaidOps`/`VaultWallet`/`WaymarkTransfer`; new PAYMENT_UNKNOWN/REFUND_UNKNOWN states; reconcile references carry op + player uuid + cost | `GrowthPaidOpsTest` 17 cases (4 new exception cases) |
| R-05 | High | FIXED | `/wayfarer-frontier gate <player>` teleported any player for anyone | `wayfarer.frontier.admin` required (self-travel via the nav GUI documented); `FrontierGate` helper | `FrontierGateTest` 3 cases |

### Priority 1 — Main / Frontier gameplay

| ID | Sev | Status | Root cause | Fix | Tests |
|---|---|---|---|---|---|
| R-06 | High | FIXED | right-click opened the GUI only for BROKEN | ACTIVE opens the status GUI too; stale-epoch/foreign/revoked items are rejected; vanilla interaction untouched for ACTIVE | code + live startup verification |
| R-07 | Medium | FIXED | GUI mapped tier 4+ → NETHERITE and hardcoded `netheriteUpgraded=false` | uses `GrowthItems.materialFor`/`tierName` (the item's source of truth) | `GrowthItemsTest` 4 cases |
| R-08 | High | FIXED | `onBreak` branch `if (tool == null \|\| status != BROKEN) { if (status == BROKEN) cancel }` could never fire | single explicit rule: BROKEN/REVOKED/foreign/stale → no mining (BROKEN cancels the event); only the owner's current ACTIVE tool registers progress | code + unit-level reasoning documented |
| R-09 | Medium | FIXED | GUI/status read the DB directly, so pending progress rolled back on screen | `viewOf()` (buffered overlay first) is the single source of truth for GUI, status, lore and evolution | `ProgressBufferTest` |
| R-10 | High | FIXED | the core is built at `y+1` but `findAt` queried the clicked block's own y → the core never resolved, GUI/discovery/lifecycle were unreachable | `WaystoneGeometry` (anchor vs core offset); `waystoneAt()` resolves any structure block; `lifecycleOperationById` for the GUI | `WaystoneGeometryTest` 7 cases |
| R-11 | High | FIXED | teleport landed on `y+1` = inside the core block | `safeArrival()` scans outward for solid ground + 2 air, skipping the structure's own column; missing world fails instead of voiding | geometry test + code |
| R-12 | High | FIXED | `removeStructure` cleared 11x11x2 blocks to AIR unconditionally, and `isWaystoneBlock` matched only the anchor | removal clears only blocks that still hold their structure material (player builds and replaced terrain survive); protection covers the whole 5x5 footprint | `WaystoneGeometryTest` |
| R-13 | Medium | FIXED | every launch with an empty chestplate minted a NEW elytra (repeatable duplication) | `ElytraDecision`: reuse equipped → equip from inventory → mint once | `ElytraDecisionTest` 4 cases |
| R-14 | High | FIXED | all 5 GUIs acted on `getCurrentItem()` without checking the clicked inventory | shared `GuiClicks.countsAsButtonClick` + slot-resolved buttons in GrowthGui, ShopGui (main), FrontierNavGui, WaystoneGui, ShopGui (frontier); drags cancelled | `GuiClicksTest` 3 cases |
| R-15 | Medium | FIXED | sell removed the item before an unverified credit; buy/shop refunds were unconfirmed | sell credits first and only then removes the item (compensating on failure); every refund leg is confirmed | code |

### Additional audit findings (fixed)

- A-01/A-02/A-03: `FrontierListener` logged every move and every interact (`[gate-debug]`, `[interact-debug]`, `[frontier-debug]`) and ran a launchpad DB query on every move event; pure teleport events were handled twice. Removed the debug logging, the gate/launchpad check now runs only when the block BELOW the player changes, and `PlayerTeleportEvent` is handled once.
- Gate arrival now lands via `safeTeleport` (same safe-ground rule as the theme teleports) instead of the raw configured arrival.
- Launchpad yaw fallback used `pad.getY() - pad.getY()` (always 0); it now reads the pad's stored `yaw`.

### Runtime verification (isolated PoC, console FIFO; RCON stays disabled)

- main (03:41) + frontier (02:48) restarted on the new jars; `Wayfarer_Main ready`, `Wayfarer_Frontier ready`, mcMMO 2.3.003-SNAPSHOT, `/shop` delegated to EconomyShopGUI, 0 mcMMO API exceptions.
- With the new jar: `/wayfarer-main status` → tier=Stone progress=0/100 epoch=1 ACTIVE; `/waymark pay <self>` rejected (P-11); `/waymark balance` 284 WM (economy untouched).
- R-03 live: the bot's status went 0/100 → 3/100 across several digs and 3/100 → 4/100 across further digs (the old code would have capped the batch at +1).
- Data integrity (verified through H2 with the servers stopped): `kanpyo_himono progress=1219 ACTIVE`, `wf_admin epoch=2 progress=1200 SILK_TOUCH ACTIVE`, the revoked history row and both test accounts are all present.

### NOT VERIFIED (Owner client required)

- GUI open/click behaviour in-game (R-06, R-14), waystone core click, GUI teleport, structure protection and cleanup in-game (R-10..R-12), launchpad elytra (R-13), frontier shop (R-15), gate permission (R-05). The protocol bot cannot deliver use-item-on/click packets through ViaVersion to 1.21.11, and its chat commands stopped being honoured after the resource-pack gate during this session.

### Residual risks

- **EXTERNAL BLOCKER**: the storage under `/home/eariver/wayfarer-dev/runtime` intermittently returned ENOENT; H2 recreated `growth.db.mv.db` from scratch twice while the server was running. No player progress was lost from the code changes and the rows above are intact, but the PoC runtime should be moved to stable storage. Verified snapshots + the recovery procedure are in `runtime/backups/r-residual-batch/`.
- Exactly-once across the economy/DB boundary is still not possible; UNKNOWN states carry reconcile references.

## Independent review follow-up batch (2026-10-14, HEAD fd588ff)

Follow-up on the R-01..R-15 batch. The review reported remaining defects and a runtime
storage anomaly; both were investigated and fixed/reported. Unit tests: **111 passed /
0 failed** (`./gradlew build` BUILD SUCCESSFUL).

### A. Defects confirmed in the pushed commit and fixed

| ID | Sev | Status | Root cause | Fix | Tests |
|---|---|---|---|---|---|
| D1 | Critical | FIXED | `Db.tx()` incremented the ThreadLocal transaction depth BEFORE `connection()`; a failed connection therefore left the depth permanently raised, so every later transaction on that thread was treated as NESTED and its `commit()` was silently skipped — writes appeared to succeed but never committed | the depth is now captured before the connection attempt and restored from that captured value on every exit path (nested, failure, connect failure) | `DbTransactionTest.connectionFailureKeepsDepthUsable` + `nestedFailureAfterConnectFailureRestoresDepth`: after a failed connect the SAME thread runs a real transaction and the row is really committed |
| D2 | High | FIXED | `onQuit()` logged "keep the overlay" but then called `progressBuffer.clearOne()` unconditionally, so a failed final DB write discarded the not-yet-saved progress forever | the quit flush keeps the overlay on failure, refuses to clear it, and logs SEVERE with owner/name/pending/persisted/tool/epoch; `onDisable` dumps every unflushed pending value | `ProgressBufferTest.pendingForFlush` (quit decision) |
| D3 | High | FIXED | the BROKEN transition used `toolOf()` (the last-checkpointed row): it persisted the stale progress and then re-set the buffer baseline to that stale value, losing every buffered break | the transition now uses `latestProgress()` and persists/keeps the newest value; the display branch does the same | `ToolInteractionRulesTest.brokenTransitionKeepsPendingProgress` + `repairKeepsProgress` |
| D4 | High | FIXED | `cacheTool()` unconditionally called `setBaseline()`, so the async join and the reissue delivery (both read the DB row) overwrote newer buffered progress | `ProgressBuffer.promote()` only adopts an authoritative row that is not older than the buffered value; `cacheTool` uses it | `ProgressBufferTest` D4 block (older row keeps buffer, newer row becomes baseline, reissue-style flow) |

### B. Fixes from the R-batch that were missing in the pushed commit (re-applied)

The previous commit lost four edits when a corrupted file was restored from HEAD mid-batch.
All four are re-applied now and verified present:

- **R-06** `onInteract`: ACTIVE tools open the status GUI on right-click again (owner/tool-id/epoch/revoked validated; the vanilla block interaction is only suppressed for BROKEN shells).
- **R-08** `onBreak`: the unreachable `if (tool == null || status != BROKEN) { if (status == BROKEN) cancel }` branch is replaced by one explicit rule — BROKEN/REVOKED/foreign/stale never mine, and BROKEN cancels the event.
- **R-11** `WaystoneManager.teleportTo`: arrives on solid ground beside the structure (searching outward, skipping the core column, two clear blocks above) instead of inside the core block; a missing world fails the teleport; the teleport result is returned.
- **R-14** the Main shop's `onClick` now uses the shared `GuiClicks.countsAsButtonClick` rule, resolves sell/buy from the slot's real icon, and cancels drags — so a matching material in the player's own inventory cannot trigger a trade.

All five GUIs (Growth, Main Shop, Frontier Nav, Waystone, Frontier Shop) now share the same
click-zone rule and cancel drags — verified by a per-file grep.

### C. Runtime storage anomaly — root cause (EXTERNAL BLOCKER)

The review asked for a read-only investigation of the `runtime` ENOENT / H2 recreation.

**Evidence gathered (read-only):**
- The H2 trace file records only one kind of database error: `MVStoreException: The file
  is locked` ×10 — i.e. a second process opening a database another process already holds.
- A controlled probe in the runtime directory: a 180 000-byte file written with
  `write + flush + fsync` and read back immediately returned `FileNotFoundError` (repeatedly),
  while a 4 KB probe file read back correctly 40/40 times. The repository directory itself
  returned ENOENT during the same window.
- A database restored byte-identical (md5-verified, 188 416 bytes, 5 rows) became an empty
  12–45 KB file after a server start/stop cycle over it — i.e. the written data never
  reached stable storage even though the read-back immediately after the write succeeded.
- `df`/`/proc/mounts`: ext4 on `/dev/sda2` (19% used, 2% inodes), no FUSE/NFS/overlay in the
  path; `/tmp` is tmpfs and behaved correctly throughout.

**Conclusion:** the platform's storage under `/home/eariver` intermittently loses written
data and returns ENOENT. This is an **environmental blocker**, not a defect of the plugin
code: no code path in this batch deletes or resets the growth database, and the restore
itself was verified byte-for-byte before the server was started.

**Contributing factor (operational):** during the earlier sessions the restart procedure
(`printf 'stop' > console.fifo`) silently failed under the ENOENT episodes, so new server
JVMs were started while previous ones were still alive — that is what produced the
`file is locked` contention. Restarts must now confirm the previous PID has exited before
starting a replacement.

**Data status:** no player progress was lost by any code change. The authoritative rows
are preserved in the verified snapshot
`runtime/backups/r-residual-batch/growth.db.recovered.mv.db`
(`kanpyo_himono progress=1219 ACTIVE`, `wf_admin epoch=2 progress=1200 SILK_TOUCH ACTIVE`,
the revoked history row, both test accounts) and the recovery procedure is documented in
that directory's README.

**Recommendation:** move the isolated PoC runtime (and the repository working copy) to
storage that survives power loss (verified tmpfs or a local disk with barrier support),
or run the runtime inside a container volume with fsync guarantees. Until then, stop the
servers by PID and verify the exit before every restart, and re-verify the database rows
after each restart.

### D. Runtime verification performed

- main restarted on the new jars: single process confirmed before and after the start,
  `Wayfarer_Main ready`, mcMMO 2.3.003-SNAPSHOT, `/shop` delegated to EconomyShopGUI,
  0 mcMMO API exceptions, 0 file-lock errors, 0 stack traces from `io.github.eariver`.
- The protocol bot joined main three times during the verification window and the async
  join + `cacheTool` path (D4) ran without a single `first-tool lookup failed` /
  `progress checkpoint failed` / `not persisted on quit` entry.
- `WaymarkProvider` and `Wayfarer_Main` load lists verified in the startup output.
- Unit/integration: 111 passed, 0 failed, including the new fault-injection and
  wiring-rule suites described above.

### E. NOT VERIFIED (documented, left for the Owner client)

- GUI open/click behaviour in-game (R-06, R-14) — the protocol bot cannot deliver
  use-item-on or inventory-click packets through ViaVersion to 1.21.11, and its chat
  commands stopped being honoured after the resource-pack gate during this window.
- Waystone teleport landing / no suffocation (R-11), structure protection and cleanup
  in-game (R-12), launchpad elytra (R-13), the frontier shop (R-15) and the gate
  permission (R-05): all require real client interaction.
- Live mining accumulation with the final jar: verified earlier in this session
  (0/100 → 3/100 → 4/100 across consecutive digs, which the old code would have capped
  at +1 per checkpoint batch); the re-verification attempt was blocked by the resource-pack
  gate described above.

### F. Residual risks

- The storage instability (C) can still destroy a database while a server is running; the
  backup + recovery procedure in `runtime/backups/r-residual-batch/` is the mitigation.
- Exactly-once across the economy/DB boundary remains impossible; UNKNOWN states carry
  reconcile references by design.

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
