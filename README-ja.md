# Foldlytics

[English](README.md)

折りたたみスマホを、実際どれくらい開いて使っていますか？

Foldlyticsは、対応する折りたたみAndroid端末で、外側・内側ディスプレイの利用時間、検出した「開いた」回数、画面別のアプリ利用状況を確認するためのアプリです。

ダウンロード：[https://play.google.com/store/apps/details?id=com.nagopy.android.foldlytics](https://play.google.com/store/apps/details?id=com.nagopy.android.foldlytics)

[Foldlyticsを作った理由と設計方針](docs/CONCEPT-ja.md)

[集計方法と制約](docs/MEASUREMENT-ja.md)

[プライバシーポリシー](https://www.nagopy.com/privacy-policy/)

## 分かること

- 外側・内側ディスプレイごとの利用時間と、内側を使った割合。
- 検出した「開いた」回数。
- 1回の内側画面利用ごとの時間の中央値・平均値・最長時間、長く使った最大3回と
  アプリ別内訳。
- 記録のある1日あたりに開いた回数。
- 記録のある日数と、内側を使った日数。
- 長期間の内側利用割合と開いた回数の推移。
- 十分な履歴がある場合の、記録開始後30日と直近30日の比較。
- 表示時間の合計、外側、または内側を基準にしたアプリ別ランキング。
- アプリごとの外側・内側割合と、割合が高い画面ごとの実時間ランキング。
- データ充足率と収集状態。
- 保存している全期間を対象にした日次CSV。

内側画面の利用は、検出した「開いた」から、その後に検出した「閉じた」までを1回として扱い
ます。状態を確認できた0秒の利用も統計に含めます。正の長さの区間で、画面やロックの状態から
利用中とも非利用とも判断できない時間が1つでもある場合は、後で状態が分かってもその回全体を
統計から除外します。画面消灯またはロック中と確認できた時間は0秒として扱い、開閉が同じ時刻
なら正当な0秒として残します。長く使った最大3回には、ランチャーから起動できるアプリを最大
3件表示し、非ランチャーアプリ、4件目以降、1つのアプリへ配分できなかった時間は「その他」
にまとめます。確実にresume中のアプリが1つだけなら、過去のアクティビティイベントから別の
アプリがresume中かもしれない場合でも、そのアプリへセッション時間を配分します。このため、
Androidの根拠が曖昧な実際の分割利用は近似される場合があります。分析にはすでに端末内へ保存
している利用状況イベントを使い、新しい権限やデータ
の自動送信は追加しません。派生キャッシュは保存済みの元イベントから再生成できます。

アプリ別ランキングは、利用時間の合計が長い順で表示します。外側・内側それぞれの利用時間順にも
切り替えられます。「よく使う画面」では、アプリごとの外側と内側の利用時間を比べ、長く使った側の
一覧に表示します。各一覧は、その画面での利用時間が長い順です。外側と内側の利用時間が同じアプリは、
どちらの一覧にも表示しません。使用した画面が不明な時間は別に表示し、合計や割合には含めません。
ランチャーから起動できるアプリだけをアイコン付きで表示します。サービスやシステム内部コンポーネントは、メイン画面ではなく
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

## ライセンス

Foldlyticsは[Apache License 2.0](LICENSE)のもとで公開しています。
