# DECISIONS — Playable Alpha

方向転換・重要な技術選定の記録。日付はJST。

## D-2026-10-11-01: Wayfarer_Core V0.0.1 は採用しない（省略・代替）

**背景**: 旧Core V0.0.1は本番ネットワーク（MariaDB + Redis + Hikari/Flyway + トランザクション保証）を前提とした共有基盤。旧V0.0.2実装（PR #14）はPR #18のSWE再設計で複数の仕様が撤回されており、そのままの再利用も適切でない。
**決定**: Coreは採用しない。本Rebootではゲーム体験に必要な最小の自作Pluginを新規実装する（Growth Pickaxe / Frontier Traversal / Waymark Provider）。永続化はSQLite（WAL）とし、本番移行時は接続先XML/YAML設定差し替えでMariaDB/Redisへ対応可能な構造にする。
**理由**: テスト環境にDBサーバーを立てる手段がない（root/docker/redis-server/mariadbなし）。ゲーム体験成立を最優先するMission方針に合致。旧CoreのTransactions/Audit/Identity等の基盤はPoC段階では必須でない。
**影響**: exactly-once等の旧保証は宣言しない。本番展開判断はOwner/ChatGPTレビュー後。

## D-2026-10-11-02: サーバー版数の選定

- Main/Lobby: Paper 26.2 build 133（manifest記録はbuild 62だがfill APIから未提供。最新stableビルドを使用）。MC版は本番と一致。
- Frontier: Paper 1.21.11 build 132（コミット c5eb079 が本番記録と完全一致）。
- Velocity: 4.1.0-SNAPSHOT build 8（git-9eb338bd-b8 一致）。本番の26.2クライアント→1.21.11 BackendはViaVersion 5.11.0で検証済み構成を維持。
- 旧plugin bundleの版数を無根拠に更新しない。Irisだけは本番同値の3.9.2をソースビルド（GitHub releaseにAssetがなく、サイズ・sha記録と実ビルドが一致）。

## D-2026-10-11-03: 通貨WaymarkのPoC代替

**背景**: 本番はRedisEconomy（SpigotMC + Owner修正ビルド）+ Redis。本サンドボックスにRedis系ランタイムがなく、修正ビルドも未入手。
**決定**: 本Repoに `waymark-provider`（Vault Economy API実装、共有SQLite-WALストレージ）を実装し、Main/Frontier双方に配置。Vault API自体はVaultUnlocked 2.20.2（Hangar/TNE、sha一致）が提供。
**影響**: クロスサーバー同一残高・送金・残高コマンド・EconomyShopGUI連携の代替。本番同等のRedis基盤はOwner判断事項として記録。

## D-2026-10-11-04: EconomyShopGUI未取得時の店代替

SpigotMCのアカウント認証により無償版JARも取得不可（403）。PoCではWayfarer Main Plugin内に固定価格Shop GUI（Vault連携）を実装し、EconomyShopGUI入手後に置換可能な構造にする。入手依頼はブロッカーリストに記載。

## D-2026-10-11-05: Velocity二重構成（本番同等＋自動検証）

- velocity（25577, online-mode=true）: Ownerの実クライアント用。本番同一設定（modern forwarding, secret=forwarding.secret）。
- velocity-offline（25588, online-mode=false）: 自動プロトコル検証クライアント用。同一forwarding.secretで同一Backendへ接続するため、Backend側設定は一枚のまま。
**理由**: 自動Runtime検証はMojang認証なしではonline-mode=true Proxyへ接続できない。両者が同じsecretを共有することでBackendの設定変更を回避。
**影響**: 本番反映時は velocity-offline を無効化/削除するだけ。

## D-2026-10-11-06: Paper 26.2の新ワールド構造への対応

Paper 26.2は単一コンテナ `world/` + `dimensions/minecraft/<key>` 方式。各サーバーのdefault world（level-name=world）をテーマ世界として直接使用し、追加ワールドはMultiverseで `world/dimensions/minecraft/<name>` に作成する。Void gate世界はbukkit.ymlの `worlds.world.generator: VoidGen` でdefault worldをVoid化する。MVのimport失敗でplugin全体がdisableされる事象（stale worlds.yml）は、worlds.yml削除→再スキャンで回避。
**影響**: 本番の `servers/*/worlds/` 構成とはディレクトリ構造が異なる。本番導入手順書に構造差異を明記する。


## D-2026-10-11-07: Owner-provided artifacts integrated (second delivery)

Owner placed the production artifact set in `~/wayfarer-dev/plugins`; all hashes recorded in
versions.yml matched. EconomyShopGUI 7.1.1 is now the Main shop and hooks the shared H2-backed
WaymarkProvider (the same Vault service the production RedisEconomy provider would occupy);
Wayfarer_Main registers `/shop` only when EconomyShopGUI is absent. Iris uses the official
3.9.2 jar. RedisEconomy stays unconfigured locally (no Redis server available) and disables
gracefully, so the PoC economy path is unchanged. CoreProtect cannot run on MC 26.2 (upstream
limitation) and remains installed-but-disabled. AdvancedPortals was not deployed because the
Velocity proxy component is required for its cross-server portals and was not included.


## D-2026-10-11-08: Production economy provider takes over (RedisEconomy)

Owner installed Redis and provided RedisEconomy 4.5.12-wayfarer.1. An isolated Redis (6399) now
runs in the sandbox; RedisEconomy on Main/Frontier (clusterId waymark) registers the Vault economy.
WaymarkProvider was changed to detect an existing Vault provider and delegate `/waymark` commands to
it instead of its own ledger (the H2 fallback only applies when no RedisEconomy is present). This
gives production parity for the shared Waymark while keeping the PoC self-contained when Redis is absent.
