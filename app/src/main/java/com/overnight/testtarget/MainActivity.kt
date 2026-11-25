package com.overnight.testtarget

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.ToggleButton
import android.app.Notification
import android.content.ClipData
import android.content.ClipboardManager
import java.util.Locale

/**
 * LSPosed モジュール (Asrish / ForceAutoRotate / HyperTranslator 等) の
 * 動作確認用ターゲットアプリ。モジュール側の設定がこのアプリにどう
 * 効いているかを、数値と見た目で確認できるようにする。
 *
 * - 現在の状態: 向き / 回転 (0/90/180/270°) / 画面サイズ (px・dp) /
 *   density / フォント倍率 / ロケール
 *   / ロック画面表示の判定 / 画面ロック状態
 * - FLAG_SECURE トグル: スクリーンショット制御の確認用
 * - ウィンドウフラグテスト: FLAG_KEEP_SCREEN_ON トグル (画面ON維持の
 *   確認用) と setShowWhenLocked トグル (ロック画面表示の確認用)。
 *   状態表示の回転と FLAG_KEEP_SCREEN_ON は向き (逆・センサー) と
 *   画面ON維持の物差し
 * - テキストサンプル: フォント倍率・DPI・翻訳の確認用
 * - 通知テスト: 通常 / 継続 / 音あり / 繰り返しの投稿と、投稿した
 *   通知オブジェクトの flags / defaults / sound の読み戻し表示
 *   (通知系機能の確認用)
 * - 音声テスト: ミュート系機能の確認用 (メディア音量でビープ音)
 * - レイアウト方向: 設定上の方向とルート View の解決済み方向の数値表示と、
 *   START / END ブロック・横並び行・Start 寄せテキストの見た目
 *   (Force LTR の確認用。RTL 言語にすると通常は全体が反転する)
 * - クリップボード: テキストのコピーと読み取り結果 (has / text) の表示
 *   (クリップボード読み取りブロックの確認用。読み取りはボタン押下時のみ
 *   行う。定期的に読むと OS の貼り付け通知がうるさいため)
 */
class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var secureStateText: TextView
    private lateinit var notifStateText: TextView
    private lateinit var layoutDirectionText: TextView
    private lateinit var clipboardStateText: TextView
    private lateinit var clipboardInput: EditText
    private lateinit var rootContent: View
    private var lastPosted: Notification? = null
    private var lastKindLabel: String? = null
    private var toneGenerator: ToneGenerator? = null

    private val handler = Handler(Looper.getMainLooper())
    private var resumedAt: Long = 0L
    private var isResumed = false

    private val ticker = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        secureStateText = findViewById(R.id.secureStateText)
        notifStateText = findViewById(R.id.notifStateText)
        layoutDirectionText = findViewById(R.id.layoutDirectionText)
        clipboardStateText = findViewById(R.id.clipboardStateText)
        clipboardInput = findViewById(R.id.clipboardInput)
        clipboardStateText.text = getString(R.string.clipboard_state_none)

        // バージョン表記は実物から取る (表記だけ古くなる事故防止)
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (t: Throwable) {
            null
        }
        findViewById<TextView>(R.id.versionText).text =
            getString(R.string.subtitle, versionName ?: "")

        // タイトルがステータスバーに重ならないようバー分の余白を足す
        rootContent = findViewById(R.id.rootContent)
        applySystemBarInsets(rootContent)
        rootContent.requestApplyInsets()

        val secureToggle = findViewById<ToggleButton>(R.id.secureToggle)
        secureToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            updateStatus()
        }

        // 画面ON維持テスト: このアプリ自身が FLAG_KEEP_SCREEN_ON を付け外しする
        val keepScreenOnToggle = findViewById<ToggleButton>(R.id.keepScreenOnToggle)
        keepScreenOnToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            updateStatus()
        }

        // ロック画面表示テスト: このアプリ自身が setShowWhenLocked を切り替える
        val showWhenLockedToggle = findViewById<ToggleButton>(R.id.showWhenLockedToggle)
        showWhenLockedToggle.setOnCheckedChangeListener { _, isChecked ->
            setShowWhenLocked(isChecked)
            updateStatus()
        }

        findViewById<Button>(R.id.notifyButton).setOnClickListener {
            postTestNotification()
        }
        findViewById<Button>(R.id.notifyOngoingButton).setOnClickListener {
            postOngoingNotification()
        }
        findViewById<Button>(R.id.notifyInsistentButton).setOnClickListener {
            postInsistentNotification()
        }
        findViewById<Button>(R.id.notifySoundButton).setOnClickListener {
            postSoundNotification()
        }
        findViewById<Button>(R.id.notifyCancelButton).setOnClickListener {
            cancelAllNotifications()
        }

        // 音声テスト: メディア音量ストリームで 2 秒のビープ音を鳴らす
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
        findViewById<Button>(R.id.soundButton).setOnClickListener {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 2000)
        }

        // クリップボードテスト: 入力欄のテキストをコピー / 読み取り結果を表示
        findViewById<Button>(R.id.clipboardCopyButton).setOnClickListener {
            val manager = getSystemService(ClipboardManager::class.java)
            manager.setPrimaryClip(
                ClipData.newPlainText("testtarget", clipboardInput.text.toString())
            )
            clipboardStateText.text = getString(R.string.clipboard_state_copied)
        }
        findViewById<Button>(R.id.clipboardReadButton).setOnClickListener {
            readClipboard()
        }

        updateStatus()
    }

    override fun onDestroy() {
        toneGenerator?.release()
        toneGenerator = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        resumedAt = SystemClock.elapsedRealtime()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        isResumed = false
        handler.removeCallbacks(ticker)
    }

    private fun updateStatus() {
        val conf = resources.configuration
        val dm = resources.displayMetrics

        val orientation = when (conf.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> getString(R.string.orientation_landscape)
            Configuration.ORIENTATION_PORTRAIT -> getString(R.string.orientation_portrait)
            else -> getString(R.string.orientation_unknown)
        }
        val widthDp = (dm.widthPixels / dm.density).toInt()
        val heightDp = (dm.heightPixels / dm.density).toInt()
        val locale: Locale = conf.locales[0]
        val secureOn =
            (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        val keepScreenOn =
            (window.attributes.flags and
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        // 回転 (0/90/180/270): 縦横だけでは区別できない「逆」と
        // センサーの効きを数値で切り分けるための読み出し
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        val rotationDeg = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val keyguardLocked =
            getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        // ロック画面表示の判定: Activity.setShowWhenLocked の状態は
        // ウィンドウフラグ (FLAG_SHOW_WHEN_LOCKED) に書き戻されず、
        // アプリ側から読み戻す公開 API もないため、フラグ直読みでは
        // 効いていても OFF と出てしまう (v0.3.0 で実機確認)。
        // 代わりに「ロック中にこの画面が前面 (resumed) で表示されて
        // いるか」で判定する。ロック画面の上に出ていなければ、
        // ロック中にこの画面は前面に来られないため、この条件が
        // そのまま「表示できている」ことの証拠になる。
        // ロックしていないときは挙動からは判定できない。
        val showWhenLockedText = if (keyguardLocked && isResumed) {
            getString(R.string.status_swl_showing)
        } else {
            getString(R.string.status_swl_unknown)
        }

        val elapsedSec = ((SystemClock.elapsedRealtime() - resumedAt) / 1000).toInt()
        val elapsed = String.format(Locale.US, "%02d:%02d", elapsedSec / 60, elapsedSec % 60)

        val onOff = if (secureOn) "ON" else "OFF"
        statusText.text = buildString {
            appendLine(getString(R.string.status_orientation, orientation))
            appendLine(getString(R.string.status_rotation, rotationDeg))
            appendLine(getString(R.string.status_screen_px, dm.widthPixels, dm.heightPixels))
            appendLine(getString(R.string.status_screen_dp, widthDp, heightDp))
            appendLine(getString(R.string.status_smallest_width, conf.smallestScreenWidthDp))
            appendLine(getString(R.string.status_density, dm.densityDpi, dm.density.toString()))
            appendLine(getString(R.string.status_font_scale, conf.fontScale.toString()))
            appendLine(
                getString(
                    R.string.status_locale,
                    locale.toLanguageTag(),
                    locale.getDisplayName(locale)
                )
            )
            appendLine(getString(R.string.status_elapsed, elapsed))
            appendLine(getString(R.string.status_flag_secure, onOff))
            appendLine(
                getString(
                    R.string.status_flag_keep_screen_on,
                    if (keepScreenOn) "ON" else "OFF"
                )
            )
            appendLine(
                getString(R.string.status_show_when_locked, showWhenLockedText)
            )
            append(
                getString(
                    R.string.status_keyguard_locked,
                    if (keyguardLocked) "ON" else "OFF"
                )
            )
        }
        secureStateText.text = getString(R.string.secure_state, onOff)

        // 投稿した通知オブジェクトの読み戻し表示
        val posted = lastPosted
        notifStateText.text = if (posted == null) {
            getString(R.string.notif_state_none)
        } else {
            buildString {
                appendLine(getString(R.string.notif_state_kind, lastKindLabel ?: ""))
                appendLine("flags: " + flagTokens(posted.flags))
                appendLine("defaults: " + defaultTokens(posted.defaults))
                append("sound: " + (posted.sound?.toString() ?: "-"))
            }
        }

        // レイアウト方向の読み戻し (Force LTR の物差し)。
        // 設定 (Configuration) 側の方向と、ルート View に実際に解決された
        // 方向の両方を出す。モジュールが View 側だけを固定する実装でも、
        // Configuration 側だけを書き換える実装でも切り分けられるように
        fun dirLabel(value: Int): String = when (value) {
            View.LAYOUT_DIRECTION_RTL -> "RTL"
            View.LAYOUT_DIRECTION_LTR -> "LTR"
            else -> value.toString()
        }
        layoutDirectionText.text = buildString {
            appendLine(
                getString(R.string.status_layout_config, dirLabel(conf.layoutDirection))
            )
            append(
                getString(R.string.status_layout_root, dirLabel(rootContent.layoutDirection))
            )
        }
    }

    private fun ensureNotificationPermission(): Boolean {
        // Android 13 以降は通知の実行時許可が必要
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            return false
        }
        return true
    }

    private fun notificationManager(): NotificationManager {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
        val soundChannel = NotificationChannel(
            SOUND_CHANNEL_ID, getString(R.string.notif_sound_channel),
            NotificationManager.IMPORTANCE_HIGH
        )
        soundChannel.setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        manager.createNotificationChannel(soundChannel)
        return manager
    }

    private fun baseBuilder(channelId: String): Notification.Builder =
        Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))

    private fun post(id: Int, kindLabel: String, notification: Notification) {
        notificationManager().notify(id, notification)
        // 投稿したオブジェクトへの参照を保持し、内容を読み戻して表示する。
        // モジュールがアプリ内プロセスのフックで投稿時にこのオブジェクトを
        // 書き換えれば、読み戻しの値も変わる (通知系機能の物差し)。
        lastPosted = notification
        lastKindLabel = kindLabel
        updateStatus()
    }

    private fun postTestNotification() {
        if (!ensureNotificationPermission()) return
        val notification = baseBuilder(CHANNEL_ID)
            .setAutoCancel(true)
            .build()
        post(ID_NORMAL, getString(R.string.notif_kind_normal), notification)
    }

    private fun postOngoingNotification() {
        if (!ensureNotificationPermission()) return
        val notification = baseBuilder(CHANNEL_ID)
            .setOngoing(true)
            .build()
        post(ID_ONGOING, getString(R.string.notif_kind_ongoing), notification)
    }

    private fun postSoundNotification() {
        if (!ensureNotificationPermission()) return
        val notification = baseBuilder(SOUND_CHANNEL_ID)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .build()
        post(ID_SOUND, getString(R.string.notif_kind_sound), notification)
    }

    private fun postInsistentNotification() {
        if (!ensureNotificationPermission()) return
        val notification = baseBuilder(SOUND_CHANNEL_ID)
            .setDefaults(Notification.DEFAULT_SOUND)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        post(ID_INSISTENT, getString(R.string.notif_kind_insistent), notification)
    }

    private fun cancelAllNotifications() {
        notificationManager().cancelAll()
        lastPosted = null
        lastKindLabel = null
        updateStatus()
    }

    /**
     * クリップボードの読み取り結果を表示する。
     * モジュールが読み取りをブロックしていれば、他のアプリでコピーした
     * 内容は has=false / text 読めず になる。このアプリ自身でコピーした
     * 内容が読めるかどうかもここで切り分けられる。
     */
    private fun readClipboard() {
        val manager = getSystemService(ClipboardManager::class.java)
        val has = try {
            manager.hasPrimaryClip()
        } catch (t: Throwable) {
            false
        }
        val clip = try {
            manager.primaryClip
        } catch (t: Throwable) {
            null
        }
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).text?.toString()
        } else {
            null
        }
        clipboardStateText.text = buildString {
            appendLine("hasPrimaryClip: $has")
            append("text: " + (text ?: getString(R.string.clipboard_state_empty)))
        }
    }

    private fun flagTokens(flags: Int): String {
        val tokens = mutableListOf<String>()
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) tokens += "ONGOING"
        if (flags and Notification.FLAG_INSISTENT != 0) tokens += "INSISTENT"
        if (flags and Notification.FLAG_AUTO_CANCEL != 0) tokens += "AUTO_CANCEL"
        if (flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) tokens += "ONLY_ALERT_ONCE"
        return if (tokens.isEmpty()) "-" else tokens.joinToString(" ")
    }

    private fun defaultTokens(defaults: Int): String {
        val tokens = mutableListOf<String>()
        if (defaults and Notification.DEFAULT_SOUND != 0) tokens += "SOUND"
        if (defaults and Notification.DEFAULT_VIBRATE != 0) tokens += "VIBRATE"
        if (defaults and Notification.DEFAULT_LIGHTS != 0) tokens += "LIGHTS"
        return if (tokens.isEmpty()) "-" else tokens.joinToString(" ")
    }

    /**
     * ステータスバー / ナビゲーションバーの高さ分だけ余白を足す。
     * targetSdk 35 ではコンテンツがバーの下に潜り込み、画面上部が
     * 隠れてしまうため (他のプロジェクトと同方針)。
     */
    private fun applySystemBarInsets(root: View) {
        val base = dp(16)
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(
                base,
                base + insets.systemWindowInsetTop,
                base,
                base + insets.systemWindowInsetBottom
            )
            insets
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CHANNEL_ID = "test_notifications"
        private const val SOUND_CHANNEL_ID = "test_notifications_sound"
        private const val ID_NORMAL = 1
        private const val ID_ONGOING = 2
        private const val ID_SOUND = 3
        private const val ID_INSISTENT = 4
    }
}
