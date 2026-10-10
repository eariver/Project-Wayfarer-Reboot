# Project Wayfarer — Autonomous Playable Alpha Mission

**Status:** Owner-directed reboot mission / 2026-10-11 JST  
**Work repository:** https://github.com/eariver/Project-Wayfarer-Reboot  
**Primary executor:** OpenCode / Step 5 Preview Free（他のAgentへの引継ぎ可能）  
**Reviewers:** Project Owner and ChatGPT  
**Objective:** 元のProject Wayfarerの遊び方を実機で体験できるPlayable Alphaを構築する。

## 1. Why this reboot exists

従来のProject Wayfarerでは、厳密なSWE設計と汎用Core実装が先行して、実際のゲーム体験を完成させるまでの負担が大きくなった。

今回の指示は**旧V0.0.2のSWE.1レビューの続行ではない**。本Rebootでは、旧プロセス、旧要件番号、旧Core採用義務、旧リリース手順を実装の前提としない。既存の技術成果は、ゲーム体験を実現するために使える範囲で採用する。

**ゲーム体験の成立を最優先**にして、調査→設計→実装→実機検証→修正を必要なだけ自律的に繰り返すこと。形式的な要求仕様書や全面的なトレーサビリティの再構築は要求しない。

ただし「自由」と「確認せず成功と称すること」は別である。再現できるBuild、実際のRuntime結果、明示した既知の不足は必要。

## 2. Reference repositories and reading priorities

1. https://github.com/eariver/Project_Wayfarer
   - README、docs/00-design-guide.md、docs/01-architecture.md、docs/09-roadmap.md、docs/14-frontier-v0.1.0-scope.md
   - concepts/frontier、concepts/plugins（old/も初期の設計意図を理解する参考）
   - servers/、plugin-manifest.yml、versions.yml、infrastructure/、scripts/
2. https://github.com/eariver/Project-Wayfarer-Plugins
   - main上のV0.0.1 Core製品・API・試験結果
   - PR #14（実装・失敗事例の参考。旧実装やその仕様を自動的に正本化しない）
   - PR #18（SWE再設計で明らかになった問題・設計改善の参考。Gate引継ぎは不要）
3. https://github.com/eariver/Project_Wayfarer_Research_Data
   - 市場調査の参考のみ。現在のPoCに不可欠ではない。

最初に、参照したbranch、HEAD SHA、文書の版、既製Pluginの採用状況を簡潔に記録する。リポジトリ記録にある「実装済み」は過去の記録であって現在のローカルServer上で動作確認済みとは限らない。

資料間の矛盾は、初期ゲーム体験、近年明示されたOwnerの意思、本Mission、安全な実機検証結果を使って合理的に解決する。大きな機能削除・概念変更はDecision Logに残す。

## 3. Product vision: what the player should experience

### A. Main — 永続的な生活拠点

- Vanillaに近いSurvival、採掘、農業、釣り、建築、冒険を楽しめる。
- 既存のBetterStructures、mcMMO、EvenMoreFish、EconomyShopGUI、WorldGuard等の設定・導入成果を可能な限り利用する。
- 独自要素はGrowth Pickaxeを第一候補とする。採掘によって成長し、素材/Enchant等が進化し、再接続や再起動後も成果が残る体験を優先する。
- 既存MainのWorld/Player資産を再生成・変更してはならない。専用のテスト環境でまず成立させる。

### B. Worlds Beyond — 未知の地形を移動・発見する体験

- Iris等の地形生成を利用した、立体移動・景観探索に特化したTheme。
- Grappling Hook、Elytra、設置式Launchpadなどを使ったTraversalを実現する。
- 可能ならWaystoneの設置・発見・移動・履歴によって、Playerが交通網を形成する体験を実装する。
- 最初は単一Overworld構成でもよい。Portal/Unknown World経由の予期せぬ流出を防ぐ。
- 既存のLeafGrapple等を活用し、フックの物理演算などを不用意に再実装しない。

### C. Ruined Frontier — 遺跡攻略と戦闘の体験

- BetterStructures、EliteMobs、Adventurer's Guild、Primis等の既製コンテンツを活用する。
- 遺跡探索、Boss戦闘、Loot、Questや装備Progressionという中核体験を成立させる。
- 専用Pluginの実装より、既存Pluginの設定・World導線・相互動作確認を優先する。
- 有償Contentや実体Artifactが手元にない場合、適法な無料/既存の代替で実験し、未充足部分を明確に残す。

### D. Network — テーマをつなぐ境界

- VelocityのLobby/Main/Frontierという基本構造は再利用候補とする。合理的な代替構成も許す。
- MainとFrontier間、Frontierの各Theme間の通常Inventory、Armor、Offhand、Ender Chest、XP等を混同・漏出させない。
- Waymark（WM）はMainとFrontierをつなぐ共有通貨として維持する。mcMMOの共有進行も既存構成の価値として尊重する。
- LuckPermsなど既存の権限管理を活用し、一般PlayerがAdmin機能を使えないことを確認する。
- World移動の出入口と帰還導線を安全に提供する。各Themeでの行き止まりを放置しない。

## 4. Implementation freedom

Agentは、**企画・技術選定・設計・実装・テスト・修正・ドキュメント整備まで一貫して担当してよい**。

許可する判断:
- Core V0.0.1を採用する、必要部分だけ参照する、再設計する、あるいは使用しない。
- 新しいPlugin構成、Gradle構成、DB設計、依存Library、内部APIを選択する。
- 安定した既製PluginやPaper APIを使い、過剰な独自実装を削減する。
- Pluginの版数互換性を調査して、検証環境で採用版を調整する。
- 最も価値が高く最短で遊べる順序を自ら決め、機能をVertical Sliceとして完結させる。
- 失敗したアプローチを破棄し、理由を残して別案を試す。
- Reboot Repository内にSource、Config Template、Tests、Scripts、Reportを作成・変更・Commit/Pushする。

許可しない判断:
- 旧3リポジトリへの書き込み、既存PR/Issueの変更、Main本番Worldまたは稼働中DBの破壊。
- 本番Networkの起動・停止・Plugin置換・DB Migrationなどへの無断介入。
- 秘密情報、JAR、Worldデータ、有償/再配布禁止ContentのGit格納。
- 問題を隠して実機試験をPASSと報告すること。
- 既存の共有通貨、所有権、World/Inventory境界を合理的説明なしに壊すこと。
- Force push、履歴の破壊的書換え、Owner承認なしの正式リリース。

**判断相談が必要なのは主に**、有償Plugin/Content等のOwnerによる提供が不可欠な場合、本番環境への変更が必要な場合、取り返しのつかない操作、または初期の中核的ゲーム体験自体を大きく変更する場合である。

## 5. Definition of Playable Alpha and staged progress

最終的な目標は、Ownerが隔離されたMinecraft環境で、以下の代表的Loopを実際に試せること。

- Mainで普通のSurvivalとGrowth Pickaxeの成長を体験できる。
- Worlds Beyondで立体移動と、少なくとも一つの独自交通設備を体験できる。
- Ruined Frontierで遺跡またはDungeonを探索し、EliteMobs等との戦闘やLootを体験できる。
- ゲート/コマンド等で安全に行き来でき、Inventoryが混ざらず、Waymark/共有進行の代表動作が成立する。

初期のすべての機能（完全なWaystone経済、すべてのGUI、全Premium Dungeon、全管理機能など）を一度に実装する必要はない。ただし、未実装のものを実装済みと称してはならない。

**推奨実行の順序（Agentは変更してよい）**:
1. 既存Plugin、依存、World構成、再利用可能CodeのInventoryを作成。
2. 隔離テストサーバーを再現可能な形で組み、必要最小限のCore相当機能を決める。
3. 最初のPlayable Vertical Sliceを完成（例: Main Growth Pickaxe）。
4. 次にWorlds BeyondまたはRuined FrontierのPlayable Loopを追加。
5. Theme間移動・持ち物分離・共有通貨を統合検証。
6. 適切なテスト、構成・導入手順、既知制約をまとめ、レビュー用成果を提出。

最初の1 Loopだけで永遠に停止せず、依存や阻害要因が許す限りPlayable Alpha全体へ進むこと。途中のCheckpointは作業再開のためのものでありOwnerの逐次承認Gateではない。

## 6. Technical and safety expectations

- 実際のPaper/Velocity/API/既存Plugin版数との互換性は、公式Source、Artifact、Runtime Logで確認する。旧バージョンを無根拠に最新版へ一括更新しない。
- MainのPaper 26.2、FrontierのPaper 1.21.11、Java 25という**旧環境の事実**を起点にしつつ、必要なら隔離PoCで代替構成を比較する。
- Minecraft Bukkit/PaperのWorld、Entity、Player、Inventory操作を適切なServer Threadで行い、DB/Network I/OをMain Threadへ持ち込まない。
- 通貨引落し、購入報酬、Item発行、再発行では二重実行・未確定失敗・再接続を考慮する。外部Economy Providerが保証しないexactly-onceを宣言しない。
- Player/World/Theme Allowlist、権限、データ整合性は実装の簡素化によって放棄しない。
- テスト用データベースとWorldは本番から物理的に隔離し、意図しないDB Migrationやデータ削除を防ぐ。
- 第三者OSSの利用条件、配布形態、帰属表示を守る。JARと有償コンテンツは通常Git追跡しない。
- Reboot RepositoryはPublic。Secret、実Player Data、Worldファイル、Database、個人パスやTokenをCommitしない。共有用ログはSanitizeする。
- 原則としてテストを優先し、操作中の事故を避けるため、本番導入はOwner/ChatGPTによる後続レビュー後の別タスクとする。

## 7. Repository workflow

- 初期調査で現在のmain SHAを確認し、現在のリポジトリ状態を記録する。
- このRepositoryで1つの開発ブランチ（例: work/playable-alpha）を作成し、そこで自律的にCommit/Pushしてよい。既存の別リポジトリへはPushしない。
- 小さな作業単位ごとに、変更理由の分かる通常Commitを残す。Force pushをしない。
- 作業が途切れても別Agentが続けられるよう、docs/PROGRESS.mdに現在のHEAD、完了した機能、次の実施事項、ブロッカーを維持する。
- 長い報告書より、再現手順・実機動作証跡・実装済み/未実装一覧を重視する。
- 実装で重要な方向転換をした場合だけdocs/DECISIONS.mdに判断理由、比較対象、影響を書き残す。
- 最終段階ではReview可能なPRを作り、Ownerが明示的に判断するまでmainへMergeしない。
- GitHub Actionsを活用してよいが、Third-party Termsや無料枠を考慮し、過剰なWorkflowを増やさない。

## 8. Required deliverables

最終PRとRepositoryに少なくとも次を用意する。

1. 実装Sourceと再現可能なBuild手順（Java/Gradle/Toolchain/依存版数を含む）。
2. 必要なPlugin JARの一覧、出典、License、配置先、必要に応じてChecksum。第三者JARそのものはCommitしない。
3. SanitizeされたConfig Templateと、隔離テストサーバー起動・停止・初期化・復旧の手順。
4. 実装機能一覧：Main、Worlds Beyond、Ruined Frontier、Networkの各機能について「実装済み/限定動作/未実装/ブロック」を明記。
5. 自動試験結果、実Paper/Velocity Runtime試験結果、可能なら実Minecraft Clientでの確認結果。**試験していない層をPASSにしない。**
6. Known limitations、依存している有償/非公開Artifactの不足、残課題、Ownerが用意する必要のある素材を明示。
7. 完了時のCommit SHA、PR URL、起動/操作手順、代表的なゲームプレイ確認項目。
8. Ownerによる本番展開判断のための安全な導入案とRollback案。本番適用そのものはしない。

## 9. Success and stop reporting

**Success**: 少なくともAlphaの代表体験を実際に試せる状態と、その根拠となるArtifact/手順/テスト証跡が存在する。

**Partial**: 一部Loopだけが動作する場合、その理由と残りの作業を具体的に示す。UnsupportedやBlockedをPASSへ置き換えない。

**Stop/ask Owner**: 法的/ライセンス上の入手制約、秘密情報や特権が必要、本番破壊の危険性、重大な設計方針転換など、Agent自身が解決してはならないものだけを明示してOwnerの判断を仰ぐ。

**Do not**: 延々と網羅的設計書を作り、最初の実装を開始しない状態へ戻らない。自律性を活かして、最初の動く機能から積み上げる。

## 10. Agent's first execution

1. このMissionと参照Repoを読み、短いゲーム体験ベースの実行計画と現状Inventoryをdocs/PROGRESS.mdへ記録。
2. Core V0.0.1を採用するか、簡素化/代替するか技術的に比較して選択。決定理由を記録。
3. 開発ブランチを作成し、隔離環境で最初のVertical Sliceの実装に着手。
4. 作成、検証、修正、Commit/Pushを継続し、可能な限りPlayable Alpha完成まで進む。
5. レビューに必要な証拠を揃えてPRを作成。進捗をチャットの一過性のContextだけに依存させない。

**Reminder**: 本Missionは、高い自律性を与えるためのゲーム体験・安全境界・出口条件の指定であり、SWE.1〜SWE.6の詳細ゲートを再導入するものではない。
