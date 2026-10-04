# Foldlytics — display balance redesign

2026-10-04。ホームで内側画面の割合を読み、アプリ別・開閉ごとの利用へ進める構成に再設計した。
画像は実際のCompose描画と合成テストデータ。個人の利用履歴は含まない。

## 画面構成と表現

- ホームは、期間と実際の集計範囲、大きな内側割合、正確な比率バー、ふたつの詳細導線、推移の順。
- 判定できた割合、記録範囲、最終更新、現在の画面は「記録の詳細」で展開して読む。判定率が記録の完全性を示すわけではないことも明記。
- 展開幅ではサマリーに並べて内側画面のアプリ上位4件を表示。文字倍率が大きい場合は1列を優先。
- アプリ詳細は「利用時間／よく使う画面」の表示切り替えと、並べ替え・絞り込みを区別。説明は一覧の後で開ける。
- 開閉ごとの詳細は中央値を主役に、平均・最長と実際の利用記録を続ける。狭幅の拡大文字では平均・最長を縦並びにする。
- 初回起動は端末の図、機能の紹介、権限の説明を確認するボタンを表示。保存済み記録があれば権限がなくても期間を選べる。

Material 3 Expressiveのテーマとモーションを使用。アイリスの紫、シトロン、白に近い寒色の背景、
同梱したManropeと日本語システム書体を組み合わせた。数字と単位の大きさを分け、48 dp以上の選択領域を確保。
角丸は外周を大きく、連続する記録の接合部を小さくする。共有画像も同じ数値表現と比率バーを使用。
ManropeのOFLライセンスはAPKのassetsに同梱する。

推移グラフはタップ・横スライドで期間ごとの値を選択でき、読み上げサービス向けの選択操作も提供する。
欠損区間をゼロと扱わず、データのない区間では線を切る。
画面遷移、選択ピル、比率バー、グラフ、説明の展開にテーマのモーションを適用。

## 撮影条件と確認内容

- 412 × 915 dp：英語ライト・ダーク、日本語。ホーム、推移、アプリ一覧、画面別一覧、セッション、メニュー、調整、診断。
- 841 × 900 dp：展開画面。同じ画面群。
- 320 × 740 dp、文字160%：ホーム、サマリー、グラフ、アプリの表示と並べ替え、セッション。
- 空データ、外側0.1%の比率、権限不足とエラー、初回起動、記録詳細、グラフの選択値、共有画像。
- 選択アニメーション80 ms時点と収束後。

計55枚。画面群のコンタクトシートと主要・境界条件の原寸画像を確認した。
撮影結果を受け、詳細画面の説明を後ろに移動し、拡大文字の選択肢・指標・軸・日付を調整。
初回起動の空の分析欄を利用開始の案内へ置き換えた。

## 変更前後

変更前はmainの `cffb3114467d1614c89cd10d3588d7967b8ef6aa` を独立した一時領域に展開し、
同じ撮影テストと撮影用依存関係だけを追加して撮影した。
本番UIは改変していない。同じ合成データ・端末寸法・言語・テーマを使用。

| 画面 | 変更前 | 変更後 |
| --- | --- | --- |
| 日本語ホーム | [before-home-ja.png](before-home-ja.png) | [home-ja.png](home-ja.png) |
| ダークホーム | [before-home-dark.png](before-home-dark.png) | [home-dark.png](home-dark.png) |
| 展開時ホーム | [before-home-expanded.png](before-home-expanded.png) | [home-expanded.png](home-expanded.png) |
| アプリ一覧 | [before-app-usage.png](before-app-usage.png) | [app-usage.png](app-usage.png) |

| 追加の確認画面 | 画像 |
| --- | --- |
| 開閉ごとの利用 | [inner-sessions.png](inner-sessions.png) |
| 狭幅・拡大文字 | [compact-home.png](compact-home.png)、[compact-controls.png](compact-controls.png)、[compact-trend.png](compact-trend.png)、[compact-sessions.png](compact-sessions.png) |
| 初回起動 | [first-launch.png](first-launch.png) |
| 記録詳細 | [record-details.png](record-details.png) |
| グラフの選択値 | [chart-inspection.png](chart-inspection.png) |
| 共有画像 | [shared-summary.png](shared-summary.png) |
| 空データ | [empty-summary.png](empty-summary.png) |
| 遷移途中・収束後 | [motion-80ms.png](motion-80ms.png)、[motion-settled.png](motion-settled.png) |

## 再現と検証

JDK 17、Android SDK 36。Roborazzi 1.76.0 / Robolectric 4.17、API 35のnative graphicsで描画。

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug -Proborazzi.test.record=true
./scripts/check-forbidden-android-permissions.sh app/build/outputs/apk/debug/app-debug.apk
git diff --check
```

ローカル260テスト成功、失敗・スキップ0件。APKビルド成功。
lintは0 errors / 38 warnings（SDK・依存更新案、widget XML、未使用リソース、KTX推奨）。
エミュレーターの対象UIテスト48件成功。修正した3クラス39件を再実行し、先に成功した他4クラス9件と合わせて確認。
禁止権限チェック、フォントライセンスのAPK同梱、`git diff --check`を確認。
全画像は `app/build/design-snapshots/` に生成される。
通常実行でも画面遷移・選択操作を検証するが、保存画像との自動ピクセル比較ではない。
グラフのタップ・ドラッグ・読み上げ選択・欠損・空／単一点・期間変更時の選択解除を回帰テストで検証。
共有画像の既存回帰テストはローカル実行に移し、大きい値、日英、期間、PNG、共有内容も確認する。

Android 16のPixel 9 Pro Foldエミュレーターでも、アプリ別画面、期間選択、読み上げ用情報、
開閉詳細、ウィジェットからのホーム復帰、権限説明のUIテストを実行。
実機の開閉・ヒンジセンサー、OS設定での権限付与、TalkBack実操作、描画フレーム時間は未検証。
リリース前の実機検証で端末名とAndroidバージョンを記録する。
