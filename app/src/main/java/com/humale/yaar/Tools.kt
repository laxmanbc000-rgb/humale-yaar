package com.humale.yaar

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.app.NotificationManager
import org.json.JSONArray
import org.json.JSONObject

object Tools {

    private fun def(name: String, desc: String, props: Map<String, Pair<String, String>>, req: List<String>): JSONObject {
        val p = JSONObject()
        props.forEach { (k, v) -> p.put(k, JSONObject().put("type", v.first).put("description", v.second)) }
        return JSONObject()
            .put("name", name)
            .put("description", desc)
            .put("input_schema", JSONObject().put("type", "object").put("properties", p).put("required", JSONArray(req)))
    }

    fun schema(): JSONArray = JSONArray().apply {
        put(def("open_app", "फोनमा एप खोल्ने", mapOf("name" to ("string" to "एपको नाम, जस्तै WhatsApp, YouTube, Camera")), listOf("name")))
        put(def("call", "फोन कल गर्ने", mapOf("to" to ("string" to "सेभ गरेको नाम वा फोन नम्बर")), listOf("to")))
        put(def("send_sms", "SMS पठाउने", mapOf(
            "to" to ("string" to "सेभ गरेको नाम वा फोन नम्बर"),
            "message" to ("string" to "सन्देश")
        ), listOf("to", "message")))
        put(def("set_alarm", "अलार्म राख्ने", mapOf(
            "hour" to ("integer" to "घण्टा, २४ घण्टे (0-23)"),
            "minute" to ("integer" to "मिनेट (0-59)"),
            "label" to ("string" to "अलार्मको नाम")
        ), listOf("hour", "minute")))
        put(def("set_timer", "टाइमर राख्ने", mapOf("seconds" to ("integer" to "सेकेन्डमा अवधि")), listOf("seconds")))
        put(def("torch", "टर्च बाल्ने/निभाउने", mapOf("on" to ("boolean" to "true = बाल्ने")), listOf("on")))
        put(def("set_volume", "मिडिया भोल्युम मिलाउने", mapOf("percent" to ("integer" to "0 देखि 100")), listOf("percent")))
        put(def("set_brightness", "स्क्रिन उज्यालो मिलाउने", mapOf("percent" to ("integer" to "0 देखि 100")), listOf("percent")))
        put(def("silent_mode", "साइलेन्ट मोड", mapOf("on" to ("boolean" to "true = साइलेन्ट")), listOf("on")))
    }

    fun run(c: Context, name: String, a: JSONObject): String = try {
        when (name) {
            "open_app" -> openApp(c, a.getString("name"))
            "call" -> call(c, a.getString("to"))
            "send_sms" -> sms(c, a.getString("to"), a.getString("message"))
            "set_alarm" -> alarm(c, a.getInt("hour"), a.getInt("minute"), a.optString("label", ""))
            "set_timer" -> timer(c, a.getInt("seconds"))
            "torch" -> torch(c, a.getBoolean("on"))
            "set_volume" -> volume(c, a.getInt("percent"))
            "set_brightness" -> brightness(c, a.getInt("percent"))
            "silent_mode" -> silent(c, a.getBoolean("on"))
            else -> "अज्ञात टुल: $name"
        }
    } catch (e: Exception) {
        "असफल भयो: ${e.message}"
    }

    private fun launch(c: Context, i: Intent) { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    private fun openApp(c: Context, query: String): String {
        val pm = c.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val q = query.trim().lowercase()
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            ?: return "'$query' नामको एप भेटिएन"
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "एप खोल्न सकिएन"
        launch(c, i)
        return "${hit.loadLabel(pm)} खोलियो"
    }

    private fun resolveNumber(c: Context, who: String): String? {
        if (who.replace(Regex("[\\s+\\-()]"), "").all { it.isDigit() } && who.any { it.isDigit() }) return who
        c.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$who%"), null
        )?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun call(c: Context, who: String): String {
        val num = resolveNumber(c, who) ?: return "'$who' को नम्बर भेटिएन"
        val uri = Uri.parse("tel:" + Uri.encode(num))
        return try {
            launch(c, Intent(Intent.ACTION_CALL, uri)); "$who लाई कल गरिँदैछ"
        } catch (e: SecurityException) {
            launch(c, Intent(Intent.ACTION_DIAL, uri)); "कल अनुमति छैन, डायलर खोलियो"
        }
    }

    private fun sms(c: Context, who: String, msg: String): String {
        val num = resolveNumber(c, who) ?: return "'$who' को नम्बर भेटिएन"
        val sm = c.getSystemService(SmsManager::class.java)
        sm.sendMultipartTextMessage(num, null, sm.divideMessage(msg), null, null)
        return "$who लाई SMS पठाइयो"
    }

    private fun alarm(c: Context, h: Int, m: Int, label: String): String {
        launch(c, Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        return "अलार्म %02d:%02d मा राखियो".format(h, m)
    }

    private fun timer(c: Context, s: Int): String {
        launch(c, Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, s)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
        return "$s सेकेन्डको टाइमर सुरु भयो"
    }

    private fun torch(c: Context, on: Boolean): String {
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "यो फोनमा फ्ल्यास छैन"
        cm.setTorchMode(id, on)
        return if (on) "टर्च बल्यो" else "टर्च निभ्यो"
    }

    private fun volume(c: Context, p: Int): String {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, max * p.coerceIn(0, 100) / 100, 0)
        return "भोल्युम $p% भयो"
    }

    private fun brightness(c: Context, p: Int): String {
        if (!Settings.System.canWrite(c)) {
            launch(c, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${c.packageName}")))
            return "अनुमति चाहिन्छ — सेटिङ खोलियो, अनुमति दिएर फेरि भन्नुस्"
        }
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(c.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 255 * p.coerceIn(0, 100) / 100)
        return "उज्यालो $p% भयो"
    }

    private fun silent(c: Context, on: Boolean): String {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (!nm.isNotificationPolicyAccessGranted) {
            launch(c, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return "'Do not disturb' अनुमति चाहिन्छ — सेटिङ खोलियो"
        }
        am.ringerMode = if (on) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
        return if (on) "फोन साइलेन्ट भयो" else "आवाज खुल्यो"
    }
}
