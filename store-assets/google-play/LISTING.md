# Google Play Store Listing

Google Play掲載用の日英テキスト案です。

文字数上限は2026-08-16時点のPlay Console公式ヘルプに基づきます。

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
- 英語ストア情報を一般公開する時点で、別途対応する英語UIと英語スクリーンショットを
  用意する。
- サポートURL、連絡先、Data safetyの回答は、提出時点の実装を再確認して入力する。

## 日本語（ja-JP）

### アプリ名

20 / 30文字

```text
Foldlytics：折りたたみ利用分析
```

### 簡単な説明

40 / 80文字

```text
外側・内側の利用時間、検出した開閉回数、アプリ別の使い方を端末内で記録できます。
```

### 詳しい説明

710 / 4,000文字

```text
折りたたみスマホを、実際どれくらい開いて使っていますか？

Foldlyticsは、対応する折りたたみAndroid端末で、外側と内側のディスプレイをどう使っているか記録するアプリです。記録を見れば、折りたたみ機能が自分の使い方に合っているか、次のスマホも折りたたみにするかを考える材料になります。

確認できること
・外側／内側ディスプレイごとの利用時間と割合
・検出した「開いた」「閉じた」回数
・内側利用割合と開いた回数の推移
・外側／内側それぞれでよく使うアプリのランキング
・プリセット期間と、日付を指定した任意期間の集計
・データ充足率と収集状態
・保存している全期間の日次CSV

利用にはAndroidの「利用状況へのアクセス」が必要です。Foldlyticsは、表示されたアプリ、画面の点灯・ロック状態、画面構成、その時刻をAndroidから読み取ります。アプリを閉じている間は、約6時間ごとのバックグラウンド同期をスケジュールします。

データは、アプリのデータを消去するかアンインストールするまで端末内に保存されます。広告・分析SDKは使用せず、外部サーバーへ自動送信しません。CSV保存と診断レポートの共有は、ユーザーが操作した場合にだけ実行します。

開閉回数は、Androidから取得できた画面構成イベントに基づく検出値です。物理ヒンジの絶対カウンターではありません。根拠のない区間は推測せず、外側・内側の統計から除外してデータ充足率に反映します。取得できるイベントは、機種やAndroidバージョンによって異なる場合があります。

Android 10以降の対応する折りたたみ端末向けです。
```

### ストア画像用の短いコピー

フィーチャーグラフィック候補:

```text
折りたたみスマホ、どのくらい開いて使っていますか？
```

スクリーンショット見出し候補:

1. `外側と内側、それぞれの利用時間が分かる`
2. `使い方の変化を週・月・年単位で確認`
3. `検出した開閉回数を期間ごとに振り返る`
4. `画面ごとによく使うアプリを比較`
5. `利用履歴は端末内だけに保存`

## English (en-US)

### App name

26 / 30 characters

```text
Foldlytics: Foldable Stats
```

### Short description

76 / 80 characters

```text
Track display time, detected opens and closes, and app usage on your device.
```

### Full description

1,529 / 4,000 characters

```text
How often do you actually unfold your phone?

Foldlytics records how you use the cover and inner displays on a compatible foldable Android device. The history can help you decide whether a foldable suits the way you use your phone, and whether you want another one next time.

What you can review
• Time spent on the cover and inner displays
• Detected open and close counts
• Trends for inner display use and detected opens
• App rankings for each display
• Preset periods and custom date ranges
• Data coverage and collection status
• Daily CSV export for all saved history

Foldlytics requires Android Usage Access. It reads which apps were displayed, whether the screen was on, whether the device was locked, display configuration, and timestamps. While the app is closed, it schedules background sync approximately every six hours.

Your data stays on your device until you clear the app's data or uninstall it. Foldlytics does not use advertising or analytics SDKs and does not automatically send data to an external server. CSV export and diagnostic report sharing occur only when you choose them.

Open and close counts come from display configuration events provided by Android. They are detections, not an absolute physical hinge counter. Foldlytics does not fill missing intervals with estimates. It leaves uncertain time out of the cover and inner display totals and reflects it in data coverage. Available events can vary by device and Android version.

Requires Android 10 or later and a compatible foldable device.
```

### Short copy for store graphics

Feature graphic candidate:

```text
How much do you use the inner display?
```

Screenshot headline candidates:

1. `See your cover and inner display time`
2. `Follow your usage trends over weeks and months`
3. `Track detected opens over time`
4. `See which apps you use on each display`
5. `Your usage history stays on your device`

## 表現上の統一ルール

- 日本語は`外側` / `内側`、英語は`cover display` / `inner display`を使う。
- `開閉回数`だけで終わらせず、本文では`検出した` / `detected`を付ける。
- `送信しない`ではなく`自動送信しない`とし、ユーザー操作によるCSV保存・共有を
  別に説明する。
- `利用時間`は端末全体、アプリ別は厳密には`表示時間`であることを、READMEと
  プライバシーポリシーで補足する。
- 対応端末を保証できないため、ストア本文では個別メーカー名を列挙せず
  `対応する折りたたみAndroid端末` / `compatible foldable Android device`とする。
