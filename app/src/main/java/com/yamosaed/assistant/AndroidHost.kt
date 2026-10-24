package com.yamosaed.assistant

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.accessibilityservice.AccessibilityService
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.yamosaed.assistant.agent.Arabic
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

    // --- مشغل الأغاني من على ملفات الجهاز نفسه ---
    private var localMediaPlayer: MediaPlayer? = null
    private var isTorchOn = false

    fun playLocalSong(songQuery: String): Pair<Boolean, String> {
        val raw = Arabic.norm(songQuery).lowercase().trim()
        val queryNorm = raw
            .replace("من على الجهاز", "")
            .replace("من على الموبايل", "")
            .replace("من الجهاز", "")
            .replace("من الموبايل", "")
            .replace("ملفات الجهاز", "")
            .replace("اغاني الجهاز", "")
            .replace("اغاني الموبايل", "")
            .replace("من عندي", "")
            .replace("محلي", "")
            .replace("شغل", "")
            .replace("اغاني", "")
            .replace("اغنية", "")
            .replace("تراك", "")
            .trim()

        val resolver = activity.contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        try {
            val cursor = resolver.query(uri, projection, selection, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

                var bestId: Long? = null
                var bestTitle: String? = null

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: ""
                    val artist = c.getString(artistCol) ?: ""
                    val full = "$title $artist".lowercase()

                    if (queryNorm.isBlank() || Arabic.norm(full).contains(queryNorm) || queryNorm.contains(Arabic.norm(title).lowercase())) {
                        bestId = id
                        bestTitle = title
                        break
                    }
                }

                if (bestId == null && c.moveToFirst()) {
                    bestId = c.getLong(idCol)
                    bestTitle = c.getString(titleCol) ?: "أغنية"
                }

                if (bestId != null) {
                    val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, bestId)
                    stopLocalMusic()
                    localMediaPlayer = MediaPlayer().apply {
                        setDataSource(activity, trackUri)
                        prepare()
                        start()
                    }
                    return true to "شغّلتلك $bestTitle من ملفات جهازك يا باشا."
                }
            }
        } catch (_: Exception) {}

        return false to "مش لاقي ملفات أغاني على ذاكرة جهازك، تحب أشغلهالك من Spotify؟"
    }

    fun stopLocalMusic(): Boolean {
        return try {
            localMediaPlayer?.stop()
            localMediaPlayer?.release()
            localMediaPlayer = null
            true
        } catch (_: Exception) {
            false
        }
    }

    // --- البحث في جهات اتصال الهاتف بالاسم ---
    fun findContactPhoneNumber(contactName: String): Pair<String?, String?> {
        val normTarget = Arabic.norm(contactName).lowercase().trim()
            .replace(Regex("^(لـ|ل|الي|إلى)\\s*"), "")
            .trim()
        if (normTarget.isBlank()) return null to null
        if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null to null
        }
        val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        try {
            val cursor = activity.contentResolver.query(uri, projection, null, null, null)
            cursor?.use { c ->
                val nameCol = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    val name = if (nameCol >= 0) c.getString(nameCol) else null
                    val num = if (numCol >= 0) c.getString(numCol) else null
                    if (name != null && num != null) {
                        val normContact = Arabic.norm(name).lowercase().trim()
                        if (normContact == normTarget || normContact.contains(normTarget) || normTarget.contains(normContact)) {
                            return name to num.replace(" ", "").replace("-", "")
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null to null
    }

    // --- إرسال الرسائل (SMS و WhatsApp) ---
    fun sendMessage(recipient: String, message: String, app: String): Pair<Boolean, String> {
        val targetApp = app.lowercase().trim()
        val text = message.ifBlank { "أهلاً بيك" }
        var phoneDigits = recipient.filter { it.isDigit() || it == '+' }
        var targetDisplayName = recipient

        // إذا كان المستلم اسماً، نبحث في جهات الاتصال
        if (phoneDigits.isBlank() && recipient.isNotBlank()) {
            val (foundName, foundNum) = findContactPhoneNumber(recipient)
            if (!foundNum.isNullOrBlank()) {
                phoneDigits = foundNum
                targetDisplayName = foundName ?: recipient
            }
        }

        if (targetApp.contains("whatsapp") || targetApp.contains("واتساب") || targetApp.contains("واتس")) {
            return try {
                val uri = if (phoneDigits.isNotBlank()) {
                    Uri.parse("https://api.whatsapp.com/send?phone=$phoneDigits&text=${Uri.encode(text)}")
                } else {
                    Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(text)}")
                }
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
                true to "فتحت لك واتساب لإرسال: \"$text\" إلى $targetDisplayName."
            } catch (_: Exception) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(Intent.createChooser(shareIntent, "إرسال عبر واتساب"))
                true to "فتحت لك خيارات الإرسال."
            }
        }

        // إرسال SMS
        return if (phoneDigits.isNotBlank() && ContextCompat.checkSelfPermission(activity, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    activity.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(phoneDigits, null, text, null, null)
                true to "بعت الرسالة بنجاح إلى $targetDisplayName ($phoneDigits) يا باشا."
            } catch (e: Exception) {
                val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneDigits")).apply {
                    putExtra("sms_body", text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(smsIntent)
                true to "فتحت لك تطبيق الرسائل لإرسالها إلى $targetDisplayName."
            }
        } else {
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${if (phoneDigits.isNotBlank()) phoneDigits else ""}")).apply {
                putExtra("sms_body", text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                activity.startActivity(smsIntent)
                true to "فتحت لك تطبيق الرسائل لإرسال: \"$text\" إلى $targetDisplayName."
            } catch (_: Exception) {
                false to "مش قادر أفتح تطبيق الرسائل."
            }
        }
    }

    // --- التحكم الشامل في خصائص وإعدادات الجهاز ---
    fun controlDevice(action: String): Pair<Boolean, String> {
        val act = Arabic.norm(action).lowercase().trim()
        return when {
            // كشاف / فلاش
            act.contains("كشاف") || act.contains("فلاش") || act.contains("نور") -> {
                val cam = activity.getSystemService(CameraManager::class.java)
                try {
                    val id = cam?.cameraIdList?.firstOrNull {
                        cam.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                    if (id != null) {
                        val turnOff = act.contains("طفي") || act.contains("اطفي") || act.contains("اقفل") || act.contains("off")
                        cam.setTorchMode(id, !turnOff)
                        isTorchOn = !turnOff
                        if (!turnOff) true to "شغّلتلك الكشاف يا باشا." else true to "طفيت لك الكشاف."
                    } else false to "الكشاف غير مدعوم في الكاميرا."
                } catch (e: Exception) {
                    false to "تعذر التحكم في الكشاف: ${e.localizedMessage}"
                }
            }
            // تعلية وخفض وكتم الصوت
            act.contains("علي الصوت") || act.contains("ارفع الصوت") || act.contains("زود الصوت") || act.contains("صوت عالي") || act.contains("volume_up") -> {
                adjustVolume(up = true)
                true to "عليت لك الصوت درجة."
            }
            act.contains("وطي الصوت") || act.contains("اخفض الصوت") || act.contains("نزل الصوت") || act.contains("صوت واطي") || act.contains("volume_down") -> {
                adjustVolume(up = false)
                true to "وطيت لك الصوت درجة."
            }
            act.contains("اكتم") || act.contains("صامت") || act.contains("ميوت") || act.contains("mute") -> {
                val am = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                true to "كتمت لك صوت الميديا تماماً."
            }
            // البطارية
            act.contains("بطاريه") || act.contains("بطارية") || act.contains("شحن") || act.contains("battery") -> {
                val bm = activity.getSystemService(BatteryManager::class.java)
                val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                if (pct >= 0) true to "نسبة شحن البطارية دلوقتي $pct% يا باشا."
                else false to "مش قادر أقرأ نسبة شحن البطارية."
            }
            // الواي فاي
            act.contains("واي فاي") || act.contains("وايفاي") || act.contains("wifi") -> {
                try {
                    activity.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true to "فتحت لك إعدادات الواي فاي."
                } catch (_: Exception) {
                    false to "تعذر فتح إعدادات الواي فاي."
                }
            }
            // البلوتوث
            act.contains("بلوتوث") || act.contains("bluetooth") -> {
                try {
                    activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true to "فتحت لك إعدادات البلوتوث."
                } catch (_: Exception) {
                    false to "تعذر فتح إعدادات البلوتوث."
                }
            }
            // الشاشة الرئيسية
            act.contains("الرئيسيه") || act.contains("الرئيسية") || act.contains("الهوم") || act.contains("home") -> {
                val ok = SearchClickService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false
                if (!ok) {
                    val i = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    activity.startActivity(i)
                }
                true to "رجعتك للشاشة الرئيسية."
            }
            // التطبيقات الأخيرة
            act.contains("التطبيقات الاخيره") || act.contains("التطبيقات الأخيرة") || act.contains("البرامج المفتوحه") || act.contains("recents") -> {
                val ok = SearchClickService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false
                if (ok) true to "فتحت لك التطبيقات الأخيرة." else false to "محتاج تفعيل إمكانية الوصول للتحكم بالتطبيقات الأخيرة."
            }
            // الإشعارات
            act.contains("اشعارات") || act.contains("إشعارات") || act.contains("الاشعارات") || act.contains("notifications") -> {
                val ok = SearchClickService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) ?: false
                if (ok) true to "فتحت لك شريط الإشعارات." else false to "محتاج تفعيل إمكانية الوصول."
            }
            // قفل الشاشة
            act.contains("اقفل الشاشه") || act.contains("اقفل الشاشة") || act.contains("قفل الشاشه") || act.contains("اطفي الشاشه") || act.contains("lock") -> {
                val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    SearchClickService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false
                } else false
                if (ok) true to "قفلت لك الشاشة." else false to "قفل الشاشة يحتاج أندرويد 9+ وإمكانية الوصول."
            }
            else -> false to "الأمر غير معروف."
        }
    }

    // --- فتح أي تطبيق مثبت على الجهاز ---
    /** Opens a Google Keep text-share draft. Android/Keep may still require the user to confirm saving. */
    fun createKeepNote(noteText: String): Pair<Boolean, String> {
        if (noteText.isBlank()) return false to "محتوى الملاحظة فاضي؛ قلّي أكتب إيه."
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, noteText)
                setPackage("com.google.android.keep")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            true to "فتحت مسودة الملاحظة في Google Keep. راجعها واضغط حفظ لو ظهر لك تأكيد؛ لسه ما أقدرش أضمن إنها اتحفظت من غير التحقق من الشاشة."
        } catch (_: Exception) {
            false to "مش قادر أفتح Google Keep. اتأكد إنه متثبت على الجهاز، وبعدها جرّب تاني."
        }
    }

    fun openApp(appName: String): Pair<Boolean, String> {
        val norm = Arabic.norm(appName).lowercase().trim()
            .replace(Regex("^(تطبيق|برنامج)\\s*"), "")
            .trim()
        val pm = activity.packageManager

        val knownAppPackages = mapOf(
            "واتس" to "com.whatsapp",
            "الواتس" to "com.whatsapp",
            "google keep notes" to "com.google.android.keep",
            "google keep" to "com.google.android.keep",
            "keep notes" to "com.google.android.keep",
            "جوجل كيب نوتس" to "com.google.android.keep",
            "جوجل كيب" to "com.google.android.keep",
            "كيب نوتس" to "com.google.android.keep",
            "keep" to "com.google.android.keep",
            "واتساب" to "com.whatsapp",
            "whatsapp" to "com.whatsapp",
            "يوتيوب" to "com.google.android.youtube",
            "اليوتيوب" to "com.google.android.youtube",
            "youtube" to "com.google.android.youtube",
            "فيس" to "com.facebook.katana",
            "الفيس" to "com.facebook.katana",
            "فيسبوك" to "com.facebook.katana",
            "الفيسبوك" to "com.facebook.katana",
            "facebook" to "com.facebook.katana",
            "انستا" to "com.instagram.android",
            "الانستا" to "com.instagram.android",
            "انستجرام" to "com.instagram.android",
            "instagram" to "com.instagram.android",
            "تليجرام" to "org.telegram.messenger",
            "التيليجرام" to "org.telegram.messenger",
            "telegram" to "org.telegram.messenger",
            "تيك توك" to "com.zhiliaoapp.musically",
            "التيك توك" to "com.zhiliaoapp.musically",
            "tiktok" to "com.zhiliaoapp.musically",
            "كروم" to "com.android.chrome",
            "الكروم" to "com.android.chrome",
            "المتصفح" to "com.android.chrome",
            "chrome" to "com.android.chrome",
            "سبوتيفاي" to "com.spotify.music",
            "spotify" to "com.spotify.music",
            "ساوندكلاود" to "com.soundcloud.android",
            "soundcloud" to "com.soundcloud.android",
            "متجر" to "com.android.vending",
            "المتجر" to "com.android.vending",
            "جوجل بلاي" to "com.android.vending"
        )

        val targetPkg = knownAppPackages.entries.firstOrNull { norm.contains(it.key) || it.key == norm }?.value

        if (targetPkg != null) {
            val intent = pm.getLaunchIntentForPackage(targetPkg)
            if (intent != null) {
                activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true to "فتحت لك تطبيق $appName يا باشا."
            }
        }

        if (norm.contains("كاميرا") || norm.contains("camera") || norm.contains("تصوير")) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                activity.startActivity(intent)
                return true to "فتحت لك الكاميرا."
            } catch (_: Exception) {}
        }

        if (norm.contains("حاسبه") || norm.contains("اله حاسبه") || norm.contains("calculator")) {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALCULATOR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                activity.startActivity(intent)
                return true to "فتحت لك الآلة الحاسبة."
            } catch (_: Exception) {}
        }

        if (norm.contains("معرض") || norm.contains("استوديو") || norm.contains("صور") || norm.contains("gallery")) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                activity.startActivity(intent)
                return true to "فتحت لك معرض الصور."
            } catch (_: Exception) {}
        }

        if (norm.contains("اعدادات") || norm.contains("ضبط") || norm.contains("settings")) {
            try {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true to "فتحت لك إعدادات الجهاز."
            } catch (_: Exception) {}
        }

        try {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                val normLabel = Arabic.norm(label)
                if (normLabel.contains(norm) || norm.contains(normLabel)) {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        return true to "فتحت لك تطبيق ${pm.getApplicationLabel(app)}."
                    }
                }
            }
        } catch (_: Exception) {}

        return false to "مش قادر ألاقي تطبيق $appName على جهازك."
    }

    // --- الاتصال برقم أو جهة اتصال ---
    fun makePhoneCall(recipient: String): Pair<Boolean, String> {
        var clean = recipient.filter { it.isDigit() || it == '+' }
        var displayName = recipient

        // إذا كان المستلم اسماً وليس رقماً مجرداً، نبحث في جهات الاتصال
        if (clean.isBlank() && recipient.isNotBlank()) {
            val (foundName, foundNum) = findContactPhoneNumber(recipient)
            if (!foundNum.isNullOrBlank()) {
                clean = foundNum
                displayName = foundName ?: recipient
            }
        }

        val dialTarget = if (clean.isNotBlank()) clean else recipient
        val uri = Uri.parse("tel:$dialTarget")

        val hasCallPermission = ContextCompat.checkSelfPermission(activity, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = if (hasCallPermission && clean.isNotBlank()) {
            Intent(Intent.ACTION_CALL, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        } else {
            Intent(Intent.ACTION_DIAL, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }

        return try {
            activity.startActivity(intent)
            true to "بتصل بـ $displayName ${if (clean.isNotBlank() && displayName != clean) "($clean)" else ""} يا باشا..."
        } catch (_: Exception) {
            false to "تعذر إجراء المكالمة لـ $displayName."
        }
    }
}