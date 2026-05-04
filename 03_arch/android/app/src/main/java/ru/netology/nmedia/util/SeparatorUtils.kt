package ru.netology.nmedia.util

import java.util.Calendar
import java.util.Date

object SeparatorUtils {
    fun getSeparatorType(published: Long): String {
        val postCalendar = Calendar.getInstance().apply { timeInMillis = published }
        val todayCalendar = Calendar.getInstance()

        val isToday = postCalendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) &&
                postCalendar.get(Calendar.DAY_OF_YEAR) == todayCalendar.get(Calendar.DAY_OF_YEAR)

        if (isToday) return "Сегодня"

        val yesterdayCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = postCalendar.get(Calendar.YEAR) == yesterdayCalendar.get(Calendar.YEAR) &&
                postCalendar.get(Calendar.DAY_OF_YEAR) == yesterdayCalendar.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) return "Вчера"

        return "На прошлой неделе"
    }

    fun formatDate(published: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = published }
        return "${calendar.get(Calendar.DAY_OF_MONTH)}.${calendar.get(Calendar.MONTH) + 1}.${calendar.get(Calendar.YEAR)}"
    }
}