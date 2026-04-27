package de.schanbro.screenity

import com.google.gson.annotations.SerializedName

data class ScreenEvent(
    val timestamp: Long,
    val type: String, // "SCREEN_ON", "SCREEN_OFF", "APP_OPEN", "APP_CLOSE"
    val packageName: String? = null
)
data class ServerSummary(
    val devices: List<DeviceSummary>,
    val total_all_devices_hours: Double,
    val today_total_all_devices_ms: Long
)

data class DeviceSummary(
    val device_id: String,
    val device_name: String,
    val days_tracked: Int,
    @SerializedName("today_ms") // Verknüpft das JSON-Feld "today_ms" mit der Kotlin-Variable
    val today_ms: Long,
    val reports: Map<String, DeviceReport>?
)

// Zur Erinnerung, falls noch nicht definiert:
data class DeviceReport(
    val total_screen_time_ms: Long,
    val apps: List<AppUsageDetail>?,
    val detailed_events: List<ScreenEvent>?
)

data class AppUsageDetail(
    val app_name: String,
    val usage_ms: Long
)

data class DayReport(
    @SerializedName("total_screen_time_ms") val totalMs: Long,
    val apps: List<AppUsageDetail>? = null
)