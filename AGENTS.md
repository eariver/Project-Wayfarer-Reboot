# AGENTS.md — Project Wayfarer Reboot

本リポジトリの実装エージェントは、まず **docs/PLAYABLE_ALPHA_MISSION.md** を全文読み、その内容を実行契約としてください。READMEだけで実装範囲を決めないこと。

## Operating mode

- **High autonomy**: 機能調査、Plugin/Libraryの取捨選択、アーキテクチャ、実装、テスト、修正、文書整備まで自律的に進める。
- **Playable-first**: SWE/Vモデルの厳密な段階承認を復活させない。動くゲームプレイ単位で実装し、検証する。
- **Reuse pragmatically**: 既存のWayfarer_Core V0.0.1も過去のMain/Frontier実装も採用義務なし。既存Plugin/APIで足りる処理は自前で再実装しない。
- **Repository authority**: このRepositoryの成果、現行Mission、Ownerの明示的な後続指示を優先する。旧Repositoryの禁止・承認ゲートを無条件に本作業へ転用しない。
- **Evidence over claims**: Build PASS、Unit Test PASS、Paper Runtime PASS、実クライアント確認を厳密に区別し、未検証は未検証と書く。
- **Checkpoints**: 適度にコミットと進捗記録を残すが、Ownerへの逐一の承認依頼は不要。

## Hard boundaries

- 書き込み対象はこのReboot Repositoryの開発用ブランチのみとする。旧Repository・Issue・PR・Releaseへの書き込みは禁止。
- 本番の既存World、Player Data、DB、Redis Volume、実際のServer配置先を変更・削除・再生成しない。隔離したテスト環境を使用する。
- Secrets、Token、Password、Forwarding Secret、Player Data、World、DB Dump、JAR、有償ContentをGitへ格納しない。ここはPublic Repository。
- 第三者成果物のライセンスを守る。不明な有償Plugin・Contentは正規取得をOwnerに依頼し、無断取得や再配布をしない。
- Force push、既存履歴のrewrite、無断の外部公開・本番デプロイをしない。
- データ完全性、Inventory/World隔離、Player権限、課金/共有通貨に影響する処理の失敗は隠蔽しない。

ブロッカー以外の細部は自分で決めて進めてよい。ブロッカーと進捗はRepositoryのドキュメントに残すこと。
