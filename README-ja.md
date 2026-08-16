# Foldlytics

[English](README.md)

折りたたみスマホを、実際どれくらい開いて使っていますか？

Foldlyticsは、対応する折りたたみAndroid端末で、外側・内側ディスプレイの利用時間、検出した開閉回数、画面別のアプリ利用状況を確認するためのアプリです。

ダウンロード：[https://play.google.com/store/apps/details?id=com.nagopy.android.foldlytics](https://play.google.com/store/apps/details?id=com.nagopy.android.foldlytics)

[Foldlyticsを作った理由と設計方針](docs/CONCEPT-ja.md)

[集計方法と制約](docs/MEASUREMENT-ja.md)

[プライバシーポリシー](https://www.nagopy.com/privacy-policy/)

## 分かること

- 外側・内側ディスプレイごとの利用時間と、内側を使った割合。
- 検出した「開いた」「閉じた」回数。
- 記録のある1日あたりに開いた回数。
- 記録のある日数と、内側を使った日数。
- 長期間の内側利用割合と開いた回数の推移。
- 十分な履歴がある場合の、記録開始後30日と直近30日の比較。
- 外側または内側での表示時間を基準にした、アプリ別ランキング。
- データ充足率と収集状態。
- 保存している全期間を対象にした日次CSV。

アプリ別ランキングは、ランチャーから起動できるアプリだけをアイコン付きで
表示します。サービスやシステム内部コンポーネントは、メイン画面ではなく
診断レポートに残します。

## ビルドとテスト

JDK 17とAndroid SDK 36を使用します。

```shell
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

一括で確認する場合は、次を実行します。

```shell
./gradlew testDebugUnitTest assembleDebug
```

デバッグAPKの出力先は`app/build/outputs/apk/debug/app-debug.apk`です。デバッグ版の
Application IDは`com.nagopy.android.foldlytics.debug`、公開版は
`com.nagopy.android.foldlytics`です。

`connectedDebugAndroidTest`は対象アプリを再インストールする場合があります。その際、
端末内データを消去する可能性があります。履歴を蓄積している端末では実行せず、
エミュレーターまたはテスト専用端末を使用してください。

## 主な構成

- `app/src/main/java/com/nagopy/android/foldlytics/`：Activity、Application、ViewModel。
- `data/`：UsageStats読み取り、同期、Room保存、集計、CSV生成。
- `model/`：不変のドメイン値と期間選択ルール。
- `ui/`：Composeのホーム、グラフ、校正、診断画面。
- `app/src/test/`：再現性のあるJVMテスト。
- `app/src/androidTest/`：データベースとComposeの実機テスト。
- `docs/`：プロダクト文書。
- `store-assets/google-play/`：Google Play掲載文とアセットのソース。

## 使用技術

- Kotlin / Jetpack Compose
- Android Gradle Plugin 9.3.0
- `UsageStatsManager.queryEvents()`
- Jetpack WindowManager
- `TYPE_HINGE_ANGLE`による診断
- Room
- WorkManager
