package de.schanbro.screenity

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

@SuppressLint("ServiceCast")
fun getDetailedEvents(context: Context): List<ScreenEvent> {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)

    val events = usageStatsManager.queryEvents(calendar.timeInMillis, System.currentTimeMillis())
    val eventList = mutableListOf<ScreenEvent>()
    val event = UsageEvents.Event()

    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        val type = when (event.eventType) {
            UsageEvents.Event.SCREEN_INTERACTIVE -> "SCREEN_ON"
            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> "SCREEN_OFF"
            UsageEvents.Event.ACTIVITY_RESUMED -> "APP_OPEN"
            UsageEvents.Event.ACTIVITY_PAUSED -> "APP_CLOSE"
            else -> null
        }

        if (type != null) {
            eventList.add(ScreenEvent(
                timestamp = event.timeStamp,
                type = type,
                packageName = if (type.startsWith("APP")) event.packageName else null
            ))
        }
    }
    return eventList
}

fun calculateHourlyUsage(events: List<ScreenEvent>): List<Pair<String, Long>> {
    val hourlyMap = MutableList(24) { 0L } // 0 bis 23 Uhr
    val sortedEvents = events.sortedBy { it.timestamp }

    var lastScreenOnTime: Long? = null

    for (event in sortedEvents) {
        when (event.type) {
            "SCREEN_ON" -> lastScreenOnTime = event.timestamp
            "SCREEN_OFF" -> {
                lastScreenOnTime?.let { onTime ->
                    val offTime = event.timestamp
                    val duration = offTime - onTime

                    // Bestimmen, in welche Stunde das fällt (vereinfacht)
                    val hour = Calendar.getInstance().apply { timeInMillis = onTime }.get(Calendar.HOUR_OF_DAY)
                    if (hour in 0..23) {
                        hourlyMap[hour] += duration
                    }
                }
                lastScreenOnTime = null
            }
        }
    }

    return hourlyMap.mapIndexed { index, ms ->
        String.format("%02d:00", index) to ms
    }
}