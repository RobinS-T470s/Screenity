package de.schanbro.screenity

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

data class ScreenEvent(
    val timestamp: Long,
    val type: String, // "SCREEN_ON", "SCREEN_OFF", "APP_OPEN", "APP_CLOSE"
    val packageName: String? = null
)

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