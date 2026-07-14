package com.example.bandcolorreact.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.bandcolorreact.R
import com.example.bandcolorreact.data.AppPrefs
import com.example.bandcolorreact.ui.MainActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MusicButtonListenerService : NotificationListenerService() {

    private val activeCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val otherAppsPlaybackState = mutableMapOf<String, Int>()

    private lateinit var bandSession: MediaSessionCompat
    private var bandSessionActive = false

    private lateinit var audioManager: AudioManager
    private var phantomPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var phantomActivationRunnable: Runnable? = null

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Perdimos el foco de audio -> cedemos")
                stopPhantomPlayer(abandonFocus = false)
                if (bandSessionActive) {
                    bandSession.isActive = false
                    bandSessionActive = false
                    updateNotification(showAsPlayer = false)
                }
            }
        }
    }

    private val networkExecutor = Executors.newSingleThreadExecutor()
    private val reaffirmHandler = Handler(Looper.getMainLooper())
    private val reaffirmRunnable = object : Runnable {
        override fun run() {
            updateBandSessionAvailability()
            reaffirmHandler.postDelayed(this, REAFFIRM_INTERVAL_MS)
        }
    }

    // Listener para apagar el reproductor fantasma en tiempo real
    private val servicePrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppPrefs.KEY_SERVICE_ENABLED) {
            Log.d(TAG, "Interruptor maestro cambió -> reevaluamos el reproductor fantasma")
            updateBandSessionAvailability()
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        AppPrefs.prefs(this).registerOnSharedPreferenceChangeListener(servicePrefsListener)
        createNotificationChannel()
        setupBandSession()
        startForegroundWithBandNotification()
        updateBandSessionAvailability()
        reaffirmHandler.postDelayed(reaffirmRunnable, REAFFIRM_INTERVAL_MS)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val playerChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_player_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_player_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(playerChannel)

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                getString(R.string.notification_channel_alert_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_alert_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setShowBadge(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun onBandButtonPressed() {
        if (!AppPrefs.isServiceEnabled(this)) return

        val triggerUrl = AppPrefs.getTriggerUrl(this)
        if (triggerUrl.isBlank()) {
            showMissingUrlNotification()
            return
        }

        showPcEncendidaNotification()
        triggerPcOnUrl(triggerUrl)
    }

    private fun showPcEncendidaNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, ALERT_NOTIFICATION_ID, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = try {
            android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_large_encenderpc)
        } catch (e: Exception) { null }

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_alert_title))
            .setContentText(getString(R.string.notification_alert_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_alert_big_text)))
            .setSmallIcon(R.drawable.ic_stat_encenderpc)
            .apply { if (largeIcon != null) setLargeIcon(largeIcon) }
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        try { getSystemService(NotificationManager::class.java).notify(ALERT_NOTIFICATION_ID, notification) }
        catch (e: SecurityException) { Log.w(TAG, "Falta permiso POST_NOTIFICATIONS") }
    }

    private fun showMissingUrlNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, ALERT_NOTIFICATION_ID, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_alert_no_url_title))
            .setContentText(getString(R.string.notification_alert_no_url_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.notification_alert_no_url_big_text)))
            .setSmallIcon(R.drawable.ic_stat_encenderpc)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        try { getSystemService(NotificationManager::class.java).notify(ALERT_NOTIFICATION_ID, notification) }
        catch (e: SecurityException) { Log.w(TAG, "Falta permiso POST_NOTIFICATIONS") }
    }

    private fun triggerPcOnUrl(triggerUrl: String) {
        networkExecutor.execute {
            try {
                val connection = URL(triggerUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.responseCode
                connection.disconnect()
            } catch (e: Exception) { Log.e(TAG, "Error trigger", e) }
        }
    }

    private fun setupBandSession() {
        bandSession = MediaSessionCompat(this, "BandColorReactSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { onBandButtonPressed() }
                override fun onPause() { onBandButtonPressed() }
            })
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "EncenderPC companion")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Listo para el botón de la pulsera")
                    .build()
            )
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE)
                    .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
                    .build()
            )
            val receiverIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                component = ComponentName(this@MusicButtonListenerService, BandMediaButtonReceiver::class.java)
            }
            val receiverPendingIntent = PendingIntent.getBroadcast(
                this@MusicButtonListenerService, 0, receiverIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setMediaButtonReceiver(receiverPendingIntent)
        }
    }

    private fun buildMediaKeyPendingIntent(keyCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            component = ComponentName(this@MusicButtonListenerService, BandMediaButtonReceiver::class.java)
            putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        }
        return PendingIntent.getBroadcast(
            this, keyCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildBandNotification(): android.app.Notification {
        val playPendingIntent = buildMediaKeyPendingIntent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
        val pausePendingIntent = buildMediaKeyPendingIntent(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_player_title))
            .setContentText(getString(R.string.notification_player_text))
            .setSmallIcon(R.drawable.ic_stat_encenderpc)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_play, "Play", playPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
            .setStyle(MediaStyle().setMediaSession(bandSession.sessionToken).setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun buildStandbyNotification(): android.app.Notification {
        val text = if (AppPrefs.isServiceEnabled(this)) getString(R.string.notification_standby_text) else getString(R.string.notification_disabled_text)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_player_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_encenderpc)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun startForegroundWithBandNotification() {
        val notification = buildBandNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(showAsPlayer: Boolean) {
        val notification = if (showAsPlayer) buildBandNotification() else buildStandbyNotification()
        try { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification) }
        catch (e: SecurityException) { Log.w(TAG, "Falta permiso POST_NOTIFICATIONS") }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshOtherAppsSessions()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) { refreshOtherAppsSessions() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { refreshOtherAppsSessions() }

    private fun refreshOtherAppsSessions() {
        val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, MusicButtonListenerService::class.java)

        val sessions = try { manager.getActiveSessions(componentName) }
        catch (e: SecurityException) { return }.filter { it.packageName != packageName }

        val currentTokens = sessions.map { it }
        activeCallbacks.keys.filter { it !in currentTokens }.forEach { controller ->
            controller.unregisterCallback(activeCallbacks[controller]!!)
            otherAppsPlaybackState.remove(controller.packageName)
        }
        activeCallbacks.keys.retainAll(currentTokens.toSet())

        for (controller in sessions) {
            if (activeCallbacks.containsKey(controller)) continue
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    handleOtherAppPlaybackState(controller.packageName, state)
                }
            }
            controller.registerCallback(callback)
            activeCallbacks[controller] = callback
            handleOtherAppPlaybackState(controller.packageName, controller.playbackState)
        }
        updateBandSessionAvailability()
    }

    private fun handleOtherAppPlaybackState(packageName: String, state: PlaybackState?) {
        val newState = state?.state ?: return
        otherAppsPlaybackState[packageName] = newState
        updateBandSessionAvailability()
    }

    private fun startPhantomPlayer() {
        if (phantomPlayer != null) return

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
        audioFocusRequest = request

        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return

        try {
            val player = MediaPlayer.create(this, R.raw.silence) ?: return
            player.setAudioAttributes(attributes)
            player.isLooping = true
            player.setVolume(0f, 0f)
            player.start()
            phantomPlayer = player
        } catch (e: Exception) { Log.e(TAG, "Error phantom player", e) }
    }

    private fun stopPhantomPlayer(abandonFocus: Boolean = true) {
        phantomPlayer?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        phantomPlayer = null

        if (abandonFocus) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        }
        audioFocusRequest = null
    }

    private fun updateBandSessionAvailability() {
        if (!::bandSession.isInitialized) return

        if (!AppPrefs.isServiceEnabled(this)) {
            cancelPendingPhantomActivation()
            if (bandSessionActive || phantomPlayer != null) {
                stopPhantomPlayer()
                setBandPlaybackState(playing = false)
                bandSession.isActive = false
                bandSessionActive = false
            }
            updateNotification(showAsPlayer = false)
            return
        }

        if (isSomeoneElsePlayingRightNow()) {
            cancelPendingPhantomActivation()
            if (bandSessionActive) {
                stopPhantomPlayer()
                setBandPlaybackState(playing = false)
                bandSession.isActive = false
                bandSessionActive = false
                updateNotification(showAsPlayer = false)
            }
        } else {
            if (bandSessionActive) {
                bandSession.isActive = true
                return
            }
            scheduleDelayedPhantomActivation()
        }
    }

    private fun isSomeoneElsePlayingRightNow(): Boolean {
        if (otherAppsPlaybackState.values.any { it == PlaybackState.STATE_PLAYING }) return true
        if (phantomPlayer == null) {
            try { if (audioManager.isMusicActive) return true } catch (e: Exception) { }
        }
        return false
    }

    private fun scheduleDelayedPhantomActivation() {
        if (phantomActivationRunnable != null) return
        val runnable = Runnable {
            phantomActivationRunnable = null
            if (!isSomeoneElsePlayingRightNow() && !bandSessionActive) {
                bandSession.isActive = true
                bandSessionActive = true
                startPhantomPlayer()
                setBandPlaybackState(playing = true)
                updateNotification(showAsPlayer = true)
            }
        }
        phantomActivationRunnable = runnable
        reaffirmHandler.postDelayed(runnable, PHANTOM_ACTIVATION_DELAY_MS)
    }

    private fun cancelPendingPhantomActivation() {
        phantomActivationRunnable?.let { reaffirmHandler.removeCallbacks(it) }
        phantomActivationRunnable = null
    }

    private fun setBandPlaybackState(playing: Boolean) {
        bandSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .setState(if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        AppPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(servicePrefsListener)
        reaffirmHandler.removeCallbacks(reaffirmRunnable)
        cancelPendingPhantomActivation()
        activeCallbacks.forEach { (controller, cb) -> controller.unregisterCallback(cb) }
        activeCallbacks.clear()
        stopPhantomPlayer()
        bandSession.isActive = false
        bandSession.release()
        networkExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MusicButtonListener"
        private const val CHANNEL_ID = "band_color_react_service"
        private const val NOTIFICATION_ID = 1001
        private const val REAFFIRM_INTERVAL_MS = 4000L
        private const val ALERT_CHANNEL_ID = "band_color_react_pc_alert"
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val PHANTOM_ACTIVATION_DELAY_MS = 1800L
    }
}