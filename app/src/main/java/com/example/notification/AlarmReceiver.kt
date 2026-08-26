package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.local.IslamicDataSource
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.calculator.PrayerTimesCalculator
import com.example.domain.calculator.PrayerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("EXTRA_TYPE") ?: "PRAYER"
        val title = intent.getStringExtra("EXTRA_TITLE") ?: "Prayer Time"
        val message = intent.getStringExtra("EXTRA_MESSAGE") ?: "It's time for prayer."

        val notifId = (System.currentTimeMillis() % 100000).toInt()
        val channelId = if (type == "PRAYER") NotificationHelper.CHANNEL_PRAYER_ID else NotificationHelper.CHANNEL_REMINDER_ID

        NotificationHelper.showNotification(
            context = context,
            notificationId = notifId,
            channelId = channelId,
            title = title,
            message = message
        )

        // Reschedule next prayer alarms
        PrayerAlarmScheduler.scheduleAllAlarms(context)
    }
}

object PrayerAlarmScheduler {

    fun scheduleAllAlarms(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val userPrefs = UserPreferencesRepository(context)
            val settings = userPrefs.userSettingsFlow.first()

            if (!settings.notificationsEnabled) return@launch

            val cal = Calendar.getInstance(TimeZone.getTimeZone(settings.timeZoneId))
            val result = PrayerTimesCalculator.calculate(
                latitude = settings.latitude,
                longitude = settings.longitude,
                date = cal,
                method = settings.calculationMethod,
                madhhab = settings.madhhab,
                highLatRule = settings.highLatitudeRule,
                timeZone = TimeZone.getTimeZone(settings.timeZoneId)
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val prayers = listOf(
                PrayerType.FAJR to (result.fajr.time to settings.fajrNotification),
                PrayerType.DHUHR to (result.dhuhr.time to settings.dhuhrNotification),
                PrayerType.ASR to (result.asr.time to settings.asrNotification),
                PrayerType.MAGHRIB to (result.maghrib.time to settings.maghribNotification),
                PrayerType.ISHA to (result.isha.time to settings.ishaNotification)
            )

            val now = System.currentTimeMillis()
            prayers.forEachIndexed { index, (prayerType, timeAndEnabled) ->
                val (timeMillis, isEnabled) = timeAndEnabled
                if (isEnabled && timeMillis > now) {
                    val intent = Intent(context, AlarmReceiver::class.java).apply {
                        putExtra("EXTRA_TYPE", "PRAYER")
                        putExtra("EXTRA_TITLE", "Time for ${prayerType.displayName} (${prayerType.arabicName})")
                        putExtra("EXTRA_MESSAGE", "Come to prayer, come to success. Hayya 'alas-Salah.")
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        100 + index,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                    )

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                        } else {
                            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                        }
                    } catch (_: Exception) {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, pendingIntent)
                    }
                }
            }
        }
    }
}
