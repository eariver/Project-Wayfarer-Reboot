# Project Wayfarer Reboot

Project Wayfarerの初期構想を、既存Minecraftサーバー資産と既製Pluginを最大限利用してPlayable Alphaへ到達させる、AIエージェント主導の再始動リポジトリです。

## Start here

- **実装エージェント**: [AGENTS.md](AGENTS.md) を読み、次に [Playable Alpha Mission](docs/PLAYABLE_ALPHA_MISSION.md) を実行してください。
- **Owner / ChatGPT**: エージェントの実装成果・動作証跡・既知制約を確認し、プレイ体験の観点で判断します。

## 原則

- 旧Wayfarer_Core V0.0.1やSWE.1〜SWE.3への準拠は義務ではありません。利用・再設計・代替を比較し、必要最小の構造でゲーム体験を成立させます。
- Mainの恒久生活、Worlds Beyondの立体探索、Ruined Frontierの戦闘探索、テーマ間の持ち物分離と共有Waymarkという初期の世界観を重視します。
- 既存のProject Wayfarer関連リポジトリは**参照元**です。このリポジトリへの実装は許可しますが、別リポジトリの変更や本番Runtimeへの導入は許可しません。
- 公開リポジトリです。秘密情報、有償Plugin/Content、MinecraftのWorld/Player/DBデータ、第三者JARをコミットしません。
- 自律的に調査・実装・テストを進め、完成した成果をOwnerとChatGPTがレビューします。

## Source repositories

- https://github.com/eariver/Project_Wayfarer — サーバー構成、当初構想、既存Plugin/Contentの設定、運用手順
- https://github.com/eariver/Project-Wayfarer-Plugins — 旧Core安定版とMain/Frontier試作、失敗事例・設計資料
- https://github.com/eariver/Project_Wayfarer_Research_Data — 市場調査（今回の実装には必須ではありません）

参照先の履歴資料と未完了の旧SWEゲートは、本Rebootの実装承認ゲートではありません。旧リポジトリ自体は変更しないでください。
