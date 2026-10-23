package com.yamosaed.assistant

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import com.yamosaed.assistant.agent.Host
import com.yamosaed.assistant.agent.MusicAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

class AndroidHost(
    private val activity: Activity,
    private val prefs: SharedPreferences
) : Host {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val musicPackages = mapOf(
        "spotify" to "com.spotify.music",
        "soundcloud" to "com.soundcloud.android"
    )

    override fun now(): Calendar = Calendar.getInstance()

    override fun setAlarm(hour: Int, minute: Int, label: String): Boolean = try {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "يا مساعد" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        activity.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    override fun setTimer(seconds: Long, label: String): Boolean {
        val manager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            try {
                activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            } catch (_: Exception) {}
            return false
        }
        val intent = Intent(activity, TimerAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            activity,
            TimerAlarmReceiver.NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + seconds * 1000L,
            pending
        )
        return true
    }

    override fun openUrl(url: String): Boolean = try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (_: Exception) {
        false
    }

    override fun weather(city: String, done: (String) -> Unit) {
        scope.launch {
            val text = withContext(Dispatchers.IO) { fetchWeather(city) }
            done(text)
        }
    }

    private fun getActiveMediaController(): MediaController? {
        val mgr = activity.getSystemService(MediaSessionManager::class.java) ?: return null
        return try {
            val sessions = mgr.getActiveSessions(ComponentName(activity, NotifListener::class.java))
            sessions.firstOrNull { session ->
                val pkg = session.packageName
                pkg == "com.spotify.music" || pkg == "com.soundcloud.android"
            } ?: sessions.firstOrNull()
        } catch (_: SecurityException) {
            null
        }
    }

    fun getNowPlayingInfo(): String? {
        val controller = getActiveMediaController() ?: return null
        val metadata = controller.metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        return if (!title.isNullOrBlank()) {
            if (!artist.isNullOrBlank()) "$title — $artist" else title
        } else null
    }

    fun adjustVolume(up: Boolean): Boolean {
        val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        return try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun musicControl(action: MusicAction): Boolean {
        val controller = getActiveMediaController() ?: return false
        val controls = controller.transportControls ?: return false
        when (action) {
            MusicAction.TOGGLE -> {
                val state = controller.playbackState?.state
                if (state == PlaybackState.STATE_PLAYING) {
                    controls.pause()
                } else {
                    controls.play()
                }
            }
            MusicAction.NEXT -> controls.skipToNext()
            MusicAction.PREV -> controls.skipToPrevious()
        }
        return true
    }

    override fun openMusicSearch(app: String, query: String): Boolean {
        val enc = Uri.encode(query)
        val pkg = musicPackages[app]
        val (nativeUri, webUri) = when (app) {
            "spotify" -> "spotify:search:$enc" to "https://open.spotify.com/search/$enc"
            "soundcloud" -> "https://soundcloud.com/search?q=$enc" to "https://soundcloud.com/search?q=$enc"
            else -> "https://www.google.com/search?q=" + Uri.encode("Twist Music $query") to "https://www.google.com/search?q=" + Uri.encode("Twist Music $query")
        }
        if (pkg != null) {
            try {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(nativeUri))
                        .setPackage(pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            } catch (_: Exception) {}
        }
        return openUrl(webUri)
    }

    override fun armClick(app: String, query: String, done: (Boolean) -> Unit): Boolean {
        val pkg = musicPackages[app] ?: return false
        return SearchClickService.instance?.arm(pkg, query) { ok ->
            activity.runOnUiThread { done(ok) }
        } ?: false
    }

    override fun load(key: String): String? = prefs.getString(key, null)

    override fun save(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    private fun getText(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchWeather(city: String): String = try {
        val q = URLEncoder.encode(city, "UTF-8")
        val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=ar&format=json"
        val geo = JSONObject(getText(geoUrl))
        val place = geo.optJSONArray("results")?.optJSONObject(0)
            ?: return "مش لاقي مدينة بالاسم ده ($city). جرّب اسم تاني زي القاهرة أو الإسماعيلية."
        val lat = place.getDouble("latitude")
        val lon = place.getDouble("longitude")
        val name = place.optString("name", city)

        val forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto"
        val current = JSONObject(getText(forecastUrl)).getJSONObject("current")

        val weatherDesc = when (current.optInt("weather_code")) {
            0 -> "صحو ومشمس"
            1, 2 -> "صحو جزئيًا مع بعض السحب"
            3 -> "غائم"
            45, 48 -> "شبورة أو ضباب"
            in 51..67 -> "رذاذ أو مطر خفيف"
            in 71..77 -> "تساقط ثلوج"
            in 80..82 -> "زخات مطر"
            in 95..99 -> "عواصف رعدية"
            else -> "حالة جوية متقلبة"
        }
        val temp = current.optDouble("temperature_2m").toInt()
        val feelsLike = current.optDouble("apparent_temperature").toInt()
        val humidity = current.optInt("relative_humidity_2m")
        val wind = current.optDouble("wind_speed_10m").toInt()

        "الطقس في $name دلوقتي $weatherDesc. الحرارة $temp° والمحسوسة $feelsLike°، الرطوبة $humidity% وسرعة الرياح $wind كم/س."
    } catch (_: Exception) {
        "مش قادر أوصل لبيانات الطقس دلوقتي. اتأكد من اتصال الإنترنت وجرّب تاني."
    }
}