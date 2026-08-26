package com.example.domain.calculator

import java.util.Calendar
import kotlin.math.floor

data class HijriDate(
    val day: Int,
    val month: Int,
    val year: Int,
    val monthNameArabic: String,
    val monthNameEnglish: String,
    val isRamadan: Boolean,
    val isSacredMonth: Boolean,
    val isWhiteDay: Boolean // 13, 14, 15 of Hijri month (Sunnah fasting)
) {
    fun formatShort(): String = "$day $monthNameEnglish $year AH"
    fun formatArabic(): String = "$day $monthNameArabic $year هـ"
}

data class IslamicEvent(
    val nameEnglish: String,
    val nameArabic: String,
    val hijriDay: Int,
    val hijriMonth: Int,
    val description: String
)

object HijriCalendarCalculator {

    private val HIJRI_MONTHS_EN = listOf(
        "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
        "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
    )

    private val HIJRI_MONTHS_AR = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الثاني",
        "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
        "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )

    val ISLAMIC_EVENTS = listOf(
        IslamicEvent("Islamic New Year", "رأس السنة الهجرية", 1, 1, "First day of Muharram marking the Hijra."),
        IslamicEvent("Day of Ashura", "يوم عاشوراء", 10, 1, "Day Prophet Musa (AS) was saved from Pharaoh."),
        IslamicEvent("Mawlid an-Nabi", "المولد النبوي", 12, 3, "Commemoration of the birth of the Prophet Muhammad ﷺ."),
        IslamicEvent("Isra and Mi'raj", "الإسراء والمعراج", 27, 7, "The Miraculous Night Journey and Ascension."),
        IslamicEvent("Mid-Sha'ban (Laylat al-Bara'at)", "ليلة النصف من شعبان", 15, 8, "Night of records and seeking forgiveness."),
        IslamicEvent("First Day of Ramadan", "أول أيام رمضان", 1, 9, "Beginning of the blessed holy month of fasting."),
        IslamicEvent("Laylat al-Qadr (Night of Power)", "ليلة القدر", 27, 9, "The Night of Decree, better than a thousand months."),
        IslamicEvent("Eid al-Fitr", "عيد الفطر المبارك", 1, 10, "Celebration of the completion of Ramadan."),
        IslamicEvent("Day of Arafah", "يوم عرفة", 9, 12, "The greatest day of Hajj, highly recommended to fast."),
        IslamicEvent("Eid al-Adha", "عيد الأضحى المبارك", 10, 12, "Feast of the Sacrifice commemorating Prophet Ibrahim (AS)."),
        IslamicEvent("Days of Tashreeq", "أيام التشريق", 11, 12, "Days of eating, drinking and remembrance of Allah.")
    )

    /**
     * Converts Gregorian calendar date to Hijri date using Kuwaiti / Umm al-Qura astronomical algorithm,
     * applying user offset adjustment (-2, -1, 0, +1, +2 days).
     */
    fun calculateHijriDate(calendar: Calendar = Calendar.getInstance(), adjustmentDays: Int = 0): HijriDate {
        val cal = Calendar.getInstance()
        cal.timeInMillis = calendar.timeInMillis
        if (adjustmentDays != 0) {
            cal.add(Calendar.DAY_OF_MONTH, adjustmentDays)
        }

        val day = cal.get(Calendar.DAY_OF_MONTH)
        var month = cal.get(Calendar.MONTH) // 0-based
        var year = cal.get(Calendar.YEAR)

        if (year < 1900) year += 1900

        var m = month + 1
        var y = year
        if (m < 3) {
            y -= 1
            m += 12
        }

        var a = floor(y / 100.0)
        var b = 2 - a + floor(a / 4.0)
        if (y < 1583) b = 0.0
        if (y == 1582) {
            if (m > 10) b = -10.0
            if (m == 10) {
                b = 0.0
                if (day > 4) b = -10.0
            }
        }

        val julianDay = floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524

        // Julian Day to Hijri
        val epochAstro = 1948439.5
        val epochCivil = 1948438.5
        val shift1 = 8.01 / 60.0

        var z = julianDay - epochCivil
        val cyc = floor(z / 10631.0)
        z -= 10631.0 * cyc
        val j = floor((z - shift1) / 354.366)
        val iy = 30.0 * cyc + j
        z -= floor(j * 354.366 + shift1)
        val im = floor((z + 28.5001) / 29.5)
        if (im == 13.0) {
            // handle edge
        }
        val id = z - floor(29.5001 * im - 29.0)

        val hijriDay = id.toInt().coerceIn(1, 30)
        val hijriMonth = im.toInt().coerceIn(1, 12)
        val hijriYear = (iy + 1).toInt()

        val isRamadan = hijriMonth == 9
        val isSacredMonth = hijriMonth == 1 || hijriMonth == 7 || hijriMonth == 11 || hijriMonth == 12
        val isWhiteDay = hijriDay in 13..15

        return HijriDate(
            day = hijriDay,
            month = hijriMonth,
            year = hijriYear,
            monthNameArabic = HIJRI_MONTHS_AR.getOrElse(hijriMonth - 1) { "" },
            monthNameEnglish = HIJRI_MONTHS_EN.getOrElse(hijriMonth - 1) { "" },
            isRamadan = isRamadan,
            isSacredMonth = isSacredMonth,
            isWhiteDay = isWhiteDay
        )
    }

    fun getUpcomingEvents(currentHijriDate: HijriDate): List<Pair<IslamicEvent, Int>> {
        return ISLAMIC_EVENTS.map { event ->
            var daysAway = (event.hijriMonth - currentHijriDate.month) * 30 + (event.hijriDay - currentHijriDate.day)
            if (daysAway < 0) {
                daysAway += 354 // Next Hijri year
            }
            event to daysAway
        }.sortedBy { it.second }
    }
}
