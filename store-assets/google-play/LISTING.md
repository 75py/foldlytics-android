# Google Play Store Listing

Google Play掲載用の日英テキスト案です。

文字数上限は2026-09-05に確認したPlay Console公式ヘルプに基づきます。

- アプリ名 / App name: 30文字
- 簡単な説明 / Short description: 80文字
- 詳しい説明 / Full description: 4,000文字
- 公式情報: <https://support.google.com/googleplay/android-developer/answer/9859152>

ストア上の名称には説明語を加えますが、端末上のアプリ名は`Foldlytics`のままにします。

- Google Play URL:
  <https://play.google.com/store/apps/details?id=com.nagopy.android.foldlytics>
- Privacy policy URL:
  <https://www.nagopy.com/privacy-policy/>

## 公開前の前提

- 日本語UIで公開する場合は日本語をデフォルトのストア言語とする。
- 日英のUIとストア画像を用意済み。提出時は各言語の画像が公開するバージョンの
  画面と一致していることを確認する。
- サポートURL、連絡先、Data safetyの回答は、提出時点の実装を再確認して入力する。

## 日本語（ja-JP）

### アプリ名

20 / 30文字

```text
Foldlytics：折りたたみ利用分析
```

### 簡単な説明

53 / 80文字

```text
折りたたみスマホの外側・内側をどれくらい使っていますか？利用時間と画面ごとによく使うアプリを振り返れます。
```

### 詳しい説明

956 / 4,000文字

```text
折りたたみスマホを、実際どれくらい開いて使っていますか？

Foldlyticsは、外側・内側の画面を使った時間や、画面ごとによく使うアプリを振り返れるアプリです。毎日の記録から、自分が折りたたみスマホをどう使っているかが見えてきます。

■ 外側と内側、どれくらい使っている？
それぞれの画面を使った時間と、内側を使った割合を確認できます。検出した「開いた」回数も表示します。

■ 開いたあと、どれくらい使っている？
開いてから閉じるまでに内側画面を使った時間を振り返れます。平均や最長時間に加え、長く使った回のアプリ別内訳も確認できます。

■ どのアプリを、どちらの画面で使っている？
よく使うアプリを利用時間順に表示します。アプリごとの外側・内側の割合や、どちらの画面で長く使っているかも分かります。

■ 使い方は変わってきた？
期間を切り替えたり、日付を指定したりして、内側の利用割合や検出した「開いた」回数の変化をグラフで確認できます。

利用サマリーは、画像にして共有できます。保存済みの全期間の記録を、日ごとに集計したCSVとして書き出すこともできます。

データは端末内に保存
利用履歴は端末内で処理し、外部サーバーへ自動送信しません。広告やアクセス解析SDKは使用していません。CSV保存、サマリー画像や診断レポートの共有は、自分で操作したときだけ行われます。保存した履歴は、Androidの設定からアプリデータを消去するか、アンインストールすると削除できます。

ご利用にあたって
Android 10以降の対応する折りたたみ端末と、Androidの「利用状況へのアクセス」の許可が必要です。表示されたアプリ、画面の点灯・ロック状態などの記録を読み取り、アプリを閉じている間も定期的に記録を更新します。

「開いた」回数はAndroidの記録から検出するため、実際の開閉をすべて数えられるとは限りません。1回ごとの利用時間は、選んだ期間内で開いてから閉じるまでを確認できた回が対象です。利用状態が分からない時間を含む回は、その統計から除きます。使用した画面が分からない時間は、外側・内側の利用時間や割合に含めません。取得できる記録は、機種やAndroidのバージョンによって異なります。
```

### ストア画像用の短いコピー

フィーチャーグラフィック候補:

```text
折りたたみスマホ、どのくらい開いて使っていますか？
```

スクリーンショット見出し候補:

1. `外側と内側、それぞれの利用時間が分かる`
2. `1回の内側画面利用時間が分かる`
3. `使い方の変化を週・月・年単位で確認`
4. `検出した「開いた」回数を期間ごとに振り返る`
5. `画面ごとによく使うアプリを比較`
6. `利用履歴は端末内だけに保存`

## English (en-US)

### App name

26 / 30 characters

```text
Foldlytics: Foldable Stats
```

### Short description

79 / 80 characters

```text
See how much you use each display on your foldable and which apps you use most.
```

### Full description

2,141 / 4,000 characters

```text
How often do you actually unfold your phone?

Foldlytics helps you look back at the time you spend on each display and the apps you use there. Discover how your foldable fits into your everyday life.

■ How much do you use each display?
See your cover and inner display time, the percentage spent on the inner display, and the number of detected opens.

■ How long do you use the inner display after opening your phone?
See how much time you spend using the inner display between opening and closing your phone. Review average and longest times, with app breakdowns for the longest sessions.

■ Which apps do you use on each display?
Find your most-used apps, ranked by usage time. See each app's cover and inner display percentages and which display you use it on more.

■ Has your usage changed?
Choose a preset period or your own date range to explore charts of your inner display share and detected opens over time.

Share your usage summary as an image, or export daily totals for all your saved history as a CSV file.

Your history stays on your device
Foldlytics processes your usage history on your device and does not automatically send it to an external server. There are no ads or analytics SDKs. CSV export, summary image sharing, and diagnostic report sharing happen only when you choose them. Delete your saved history by clearing the app's data in Android settings or uninstalling the app.

Before you start
You need a compatible foldable device running Android 10 or later and Android Usage Access permission. Foldlytics reads records of which apps were displayed, screen and lock states, and related events. It updates your history periodically even while the app is closed.

Open counts are detected from Android's records, so they may not capture every physical unfold. Per-opening statistics include sessions where both the opening and closing were detected within your chosen period. Sessions containing time with an uncertain usage state are left out of those statistics. Time with an unknown display is excluded from cover and inner display totals and percentages. Available records vary by device and Android version.
```

### Short copy for store graphics

Feature graphic candidate:

```text
How much do you use the inner display?
```

Screenshot headline candidates:

1. `See your cover and inner display time`
2. `See inner-display use for each opening`
3. `Follow your usage trends over weeks and months`
4. `Track detected opens over time`
5. `See which apps you use on each display`
6. `Your usage history stays on your device`

## 表現上の統一ルール

- 機能名の列挙よりも、利用者が何を知り、振り返れるかを先に伝える。
- 0秒の扱いや内訳の件数など、細かな集計ルールは[集計方法と制約](../../docs/MEASUREMENT-ja.md)
  に任せ、ストア本文には利用判断に必要な制約を残す。
- バージョン間の変更説明ではなく、初めて読む人に現在のアプリを説明する。
- 日本語は`外側` / `内側`、英語は`cover display` / `inner display`を使う。
- `開いた回数`だけで終わらせず、本文では`検出した` / `detected`を付ける。
- `送信しない`ではなく`自動送信しない`とし、ユーザー操作によるCSV保存・共有を
  別に説明する。
- `利用時間`は端末全体、アプリ別は厳密には`表示時間`であることを、READMEと
  プライバシーポリシーで補足する。
- 対応端末を保証できないため、ストア本文では個別メーカー名を列挙せず
  `対応する折りたたみAndroid端末` / `compatible foldable Android device`とする。
