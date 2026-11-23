# ModuleTestTarget

LSPosed モジュールの動作確認用ターゲットアプリ。通常の Android
アプリで、モジュールではない。スコープにこのアプリを指定して、
モジュール側の設定がどう効くかを数値と見た目で確認する。

- パッケージ名: com.overnight.testtarget
- 現在の版: v0.6.0 (versionCode 11)
- 依存ライブラリなし (Android 標準 API のみ)
- 画面は縦固定、UI は 11 言語対応、ダークテーマは AMOLED 純黒

## 画面の内容

### 現在の状態 (0.5 秒ごとに自動更新)
向き、画面サイズ (px / dp)、density、フォント倍率、ロケール、
画面の表示継続時間、FLAG_SECURE の有無、ロック画面表示フラグと
画面ロック状態、最小幅 (smallestScreenWidthDp)。

### スクリーンショット制御テスト
FLAG_SECURE の ON / OFF トグル。モジュールで「許可する /
禁止する」を切り替えた結果が撮れるかどうかで判定できる。

### テキストサンプル
12 / 16 / 20 / 28sp の日本語と、英・韓・中の文。フォント倍率・
DPI の見え方、翻訳モジュールの確認に。

### 通知テスト
テスト通知の投稿ボタン (5 種) と、最後に投稿された通知の
読み戻し (flags / defaults / sound)。通知系モジュールの
設定が投稿にどう反映されたかで判定できる。

### レイアウト方向テスト
設定の方向とルート View の方向の読み出し + START / END の
目印。RTL 言語 + Force LTR の確認に。

### クリップボードテスト
入力欄 + コピーボタン + 読み取りボタン + 結果表示 (has / text)。
読み取りはボタン押下時のみ。クリップボード読み取りブロックの
確認に。

### 音声テスト
テスト音を出すボタン (メディア音量でビープ音 2 秒)。
ミュート機能の確認に。

## 変更履歴
- v0.6.0: クリップボードテスト節を追加
- v0.5.0: レイアウト方向テスト節を追加
- v0.4.0: 通知テストを拡張 (投稿ボタン 5 種 + 通知の読み戻し)

## ライセンス
Apache License 2.0

---

# ModuleTestTarget (English)

A target app for verifying LSPosed modules. A plain Android app,
not a module. Scope this app in a module and check, in numbers
and on screen, what the module's settings actually do to it.

- Package: com.overnight.testtarget
- Current version: v0.6.0 (versionCode 11)
- No dependencies (Android standard APIs only)
- Portrait-locked, UI in 11 languages, AMOLED pure-black dark theme

## What it shows

### Live state (auto-refresh every 0.5 s)
Orientation, screen size (px / dp), density, font scale, locale,
screen-on duration, FLAG_SECURE, show-when-locked flag and
keyguard state, smallest width (smallestScreenWidthDp).

### Screenshot control test
FLAG_SECURE on/off toggle — check whether screenshots can or
cannot be taken after the module changes the setting.

### Text samples
Japanese at 12 / 16 / 20 / 28sp plus one sentence each in
English, Korean and Chinese — for font scale, DPI and
translation checks.

### Notification test
Five post buttons plus a readback of the last posted
notification (flags / defaults / sound) — for checking what a
notification module did to the post.

### Layout direction test
Configuration direction and root-view direction readouts plus
START / END markers — for RTL language + force-LTR checks.

### Clipboard test
Input field + copy / read buttons + result readout (has / text).
Reads only when the button is pressed — for checking clipboard
read blocking.

### Sound test
A button that plays a 2-second beep on the media stream —
for mute checks.

## Changelog
- v0.6.0: added the clipboard test section
- v0.5.0: added the layout direction test section
- v0.4.0: expanded the notification test (5 post buttons +
  notification readback)

## License
Apache License 2.0
