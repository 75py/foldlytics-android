# Expressive redesign — visual review

2026-10-04。途中のデザイン実装を引き継ぎ、実際のCompose描画をRoborazziで撮影して修正した記録。
掲載画像はすべて合成テストデータ。個人の利用履歴は含まない。

## デザイン

Material 3 Expressiveを基盤に、生成りの紙面と濃紺、内側の青と外側のオレンジを使用。
セリフ体の数値とサンセリフ体の単位、ヒンジを思わせる小さな接合角と大きな外周角を共通化。
画面遷移、選択ピル、折りたたみ状態のグリフ、グラフの描画にテーマのモーションを使用。
展開幅ではホームのサマリーと推移を2列に配置する。

## 撮影・改善

- 412 × 915 dp：英語ライト・ダーク、日本語。ホーム、推移、アプリ一覧、画面別一覧、セッション、メニュー、調整、診断。
- 841 × 900 dp：展開画面。同じ画面群を撮影。
- 320 × 740 dp、文字倍率1.6：ホーム、サマリー、グラフ、アプリの選択操作。
- 空データ、外側0.1%の比率、権限不足とエラー、アニメーション80 ms時点と収束後。
- 計50枚を生成。全体のコンタクトシートと主要・境界条件の原寸画像で視認確認。

撮影で見つけて修正した内容：

1. サマリーの色面幅を36〜64%に制限していた表示を変更。値のパネルは等幅で読みやすく保ち、独立したバーで実際の比率を表示。
2. ごく小さい比率がバーの装飾用の隙間に消されないよう隙間の幅を制限。
3. 文字拡大時の「Cover」の単語途中の折り返しを解消。選択肢が収まらない幅では縦並びに変更し、操作領域は48 dp以上。
4. 記録状態の省略を解消。狭い画面では次の行に表示。
5. グラフの軸幅を文字倍率に追従させ、日付が年の途中で折り返されない配置に変更。
6. 小さな折りたたみグリフを描画領域に比例させ、右から左への配置でも選択表示の位置が一致するよう補正。

## 確認画像

| 条件 | 画像 |
| --- | --- |
| 日本語ホーム | [home-ja.png](home-ja.png) |
| ダークホーム | [home-dark.png](home-dark.png) |
| 展開時ホーム | [home-expanded.png](home-expanded.png) |
| アプリ一覧 | [app-usage.png](app-usage.png) |
| 開閉ごとの利用 | [inner-sessions.png](inner-sessions.png) |
| 狭幅・拡大文字のホーム | [compact-home.png](compact-home.png) |
| 狭幅・拡大文字の選択肢 | [compact-controls.png](compact-controls.png) |
| 狭幅・拡大文字の日付 | [compact-trend.png](compact-trend.png) |
| 空データ | [empty-summary.png](empty-summary.png) |
| 遷移途中 | [motion-80ms.png](motion-80ms.png) |
| 遷移収束後 | [motion-settled.png](motion-settled.png) |

## 再現と検証結果

JDK 17、Android SDK 36。Roborazzi 1.76.0 / Robolectric 4.17、API 35のnative graphicsで描画。

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug -Proborazzi.test.record=true
```

244テスト成功、失敗・スキップ0。APKビルド成功。lintは0 errors / 28 warnings。
lint警告はSDK・依存関係の更新案、既存widget XMLの小さい文字・互換属性・入れ子weight、KTX推奨。
`git diff --check`成功。

全撮影画像は `app/build/design-snapshots/` に生成される。
記録用プロパティを付けない通常実行でも画面遷移・選択操作テストを行うが、画像比較による自動回帰判定ではない。

実機の折りたたみ・ヒンジセンサー、OS設定での権限付与、TalkBackの実操作、実機フレーム時間は未検証。
リリース前に実機検証担当が端末名とAndroidバージョンを添えて確認する。
受賞水準への到達はスクリーンショット検証だけで断定せず、実機での使い心地とユーザー評価も必要。

## PR用の変更前後比較

変更前はmainの `cffb3114467d1614c89cd10d3588d7967b8ef6aa` を独立した一時領域に展開し、
本PRと同じ `DesignSnapshotTest` と撮影用依存関係だけを追加して撮影した。
変更前の本番UIコードは改変していない。同じ合成データ・端末寸法・言語・テーマを使用。
変更前の撮影テスト4件も成功。

| 画面 | 変更前 | 変更後 |
| --- | --- | --- |
| 日本語ホーム | [before-home-ja.png](before-home-ja.png) | [home-ja.png](home-ja.png) |
| ダークホーム | [before-home-dark.png](before-home-dark.png) | [home-dark.png](home-dark.png) |
| 展開時ホーム | [before-home-expanded.png](before-home-expanded.png) | [home-expanded.png](home-expanded.png) |
| アプリ一覧 | [before-app-usage.png](before-app-usage.png) | [app-usage.png](app-usage.png) |

APKの禁止権限チェックも成功（`scripts/check-forbidden-android-permissions.sh`）。
