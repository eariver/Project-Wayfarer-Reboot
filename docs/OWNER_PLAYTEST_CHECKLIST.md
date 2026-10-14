# Owner Playtest Checklist (official client, 127.0.0.1:25577)

Account: your Microsoft account (already linked in HMC, or use the normal launcher).
Everything below is running now; each item states what to do and what "pass" looks like.
Items marked **[AUTOMATED]** were already verified without a client (see DELIVERABLE_REPORT).

## 0. Connect

1. Launch Minecraft 26.2 → Multiplayer → Direct connect `127.0.0.1:25577` → Join.
2. Expect: Lobby void platform (17x17 stone, gold center), TAB header "Project Wayfarer".

## 1. Main — Growth Pickaxe (vertical slice) **[AUTOMATED: verified]**

1. `/server main` (or the lobby gate) → first join grants the Stone Pickaxe of Growth (chat message).
2. Mine ~100 blocks → expect `Your Pickaxe of Growth evolved to Iron!`; item becomes Iron Pickaxe.
3. `/wayfarer-main status` (or right-click the pickaxe) → GUI shows tier/progress/branch.
4. Break it nearly, then `/waymarer-main repair` → costs WM (balance shown by `/waymark balance`).
5. `/waymarer-main branch silk` → next Fortune evolution becomes Silk Touch.

## 2. Waymark economy **[AUTOMATED: verified cross-server]**

1. `/shop` → EconomyShopGUI opens (hooked to the shared ledger).
2. Buy or sell anything → `/waymark balance` changes accordingly.
3. `/server frontier` → `/waymark balance` shows the **same** balance.

## 3. Frontier — Worlds Beyond

1. In the Frontier gate world, walk onto the **left wool plate** (quartz gate at -5,63,0) → teleports to `frontier_iris` with the traversal loadout (Elytra, Grappling Hook, 2 Launchpads, Navigation compass). **[AUTOMATED: gate + loadout verified]**
2. Right-click the compass → Navigation GUI: Worlds Beyond / Ruined Frontier / Waymark Shop / Return to Gate.
3. Grapple: hold the hook, right-click a distant block → you are pulled there; glide with the Elytra.
4. Launchpad: place a pad on solid ground, walk onto it → you are launched upward with the Elytra auto-deployed. Pad survives 3 launches, any player can break it (no drop).
5. Waystones: buy the placement tool in the Frontier shop (600 WM) → right-click solid ground in `frontier_iris` → a 5x5 structure builds; interact the core to discover; open the waystone GUI from the core or the navigation item → Teleport.

## 4. Ruined Frontier

1. Navigation GUI → Ruined Frontier → `frontier_bs` (difficulty HARD).
2. Expect EliteMobs elites spawning naturally; kill one → elite loot drops.
3. Open `/mcstats` → the same mining level you earned on Main (shared mcMMO). **[AUTOMATED: verified via console]**

## 5. Theme inventory separation **[AUTOMATED: group data verified; live swap pending]**

1. In `frontier_iris`, hold your Main-survival items — they are NOT carried into Frontier (each server/group has its own inventory).
2. Between `frontier_iris` and the gate/`frontier_bs`, inventories swap separately (Multiverse-Inventories groups worlds_beyond / guild / neutral).
3. Verify: hold a diamond in `frontier_bs`, switch to `frontier_iris` → the diamond stays in the b's group inventory.

## 6. Permissions

1. As a normal (non-op) player: `/wayfarer-frontier admin-simulate ...`, `/waymark give ...`, `/mcmmo ...` admin paths → "You may not do that"/unknown command. **[AUTOMATED: op-gating verified]**
2. Server-side: `/lp group <name> create` on Main appears on Frontier (shared LuckPerms). **[AUTOMATED: verified]**

## 7. Optional: AdvancedPortals

Portal creation is in-game only: select a region with WorldEdit, `//copy`, at the destination `//paste`, then `/portal create <name>` with a destination `server:main` (proxy component deployed). Not yet exercised in the PoC.

## Known-good shortcuts (console, if needed)

- `/server main|frontier|lobby` from the proxy.
- Teleport to the Worlds Beyond gate plate from the Frontier gate world: stand on the **left** quartz gate plate.
