package com.optirest.eyecare.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyUsageRecord(
    val dateKey: String,
    val activeScreenMs: Long,
    val breaksCompleted: Int,
    val breaksSkipped: Int,
    val maxContinuousMs: Long,
    val hourlyMs: LongArray,
    val avgStrainScore: Int
)

data class AppConfig(
    val workMinutes: Int = 20,
    val breakSeconds: Int = 20,
    val dailyLimitMinutes: Int = 360,
    val autoStartOnBoot: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val filterEnabled: Boolean = false,
    val filterWarmthKelvin: Int = 3200,
    val filterAlphaPercent: Int = 26
)

class UsageRepository(context: Context) {
    private val prefs = context.getSharedPreferences("optirest_native_prefs", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun todayKey(): String = dateFormat.format(Date())

    fun loadConfig(): AppConfig {
        return AppConfig(
            workMinutes = prefs.getInt("work_minutes", 20).coerceIn(5, 90),
            breakSeconds = prefs.getInt("break_seconds", 20).coerceIn(10, 300),
            dailyLimitMinutes = prefs.getInt("daily_limit_minutes", 360).coerceIn(60, 720),
            autoStartOnBoot = prefs.getBoolean("auto_start_boot", true),
            notificationsEnabled = prefs.getBoolean("notifications_enabled", true),
            soundEnabled = prefs.getBoolean("sound_enabled", true),
            vibrateEnabled = prefs.getBoolean("vibrate_enabled", true),
            filterEnabled = prefs.getBoolean("filter_enabled", false),
            filterWarmthKelvin = prefs.getInt("filter_warmth_kelvin", 3200).coerceIn(1800, 6500),
            filterAlphaPercent = prefs.getInt("filter_alpha_percent", 26).coerceIn(0, 65)
        )
    }

    fun saveConfig(config: AppConfig) {
        prefs.edit()
            .putInt("work_minutes", config.workMinutes)
            .putInt("break_seconds", config.breakSeconds)
            .putInt("daily_limit_minutes", config.dailyLimitMinutes)
            .putBoolean("auto_start_boot", config.autoStartOnBoot)
            .putBoolean("notifications_enabled", config.notificationsEnabled)
            .putBoolean("sound_enabled", config.soundEnabled)
            .putBoolean("vibrate_enabled", config.vibrateEnabled)
            .putBoolean("filter_enabled", config.filterEnabled)
            .putInt("filter_warmth_kelvin", config.filterWarmthKelvin)
            .putInt("filter_alpha_percent", config.filterAlphaPercent)
            .apply()
    }

    fun loadCycleElapsedMs(): Long {
        val savedDate = prefs.getString("cycle_date", "")
        if (savedDate != todayKey()) return 0L
        return prefs.getLong("cycle_elapsed_ms", 0L).coerceAtLeast(0L)
    }

    fun saveCycleElapsedMs(ms: Long) {
        prefs.edit()
            .putString("cycle_date", todayKey())
            .putLong("cycle_elapsed_ms", ms.coerceAtLeast(0L))
            .apply()
    }

    fun loadDailyRecord(dateKey: String = todayKey()): DailyUsageRecord {
        val rawMap = loadAllRecordsJson()
        val obj = rawMap.optJSONObject(dateKey) ?: return emptyRecord(dateKey)
        val hoursJson = obj.optJSONArray("hourlyMs")
        val hourly = LongArray(24) { idx ->
            hoursJson?.optLong(idx, 0L) ?: 0L
        }
        return DailyUsageRecord(
            dateKey = dateKey,
            activeScreenMs = obj.optLong("activeScreenMs", 0L),
            breaksCompleted = obj.optInt("breaksCompleted", 0),
            breaksSkipped = obj.optInt("breaksSkipped", 0),
            maxContinuousMs = obj.optLong("maxContinuousMs", 0L),
            hourlyMs = hourly,
            avgStrainScore = obj.optInt("avgStrainScore", 0)
        )
    }

    fun loadLastNDays(days: Int): List<DailyUsageRecord> {
        val cal = Calendar.getInstance()
        val list = mutableListOf<DailyUsageRecord>()
        for (i in (days - 1) downTo 0) {
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val key = dateFormat.format(cal.time)
            list.add(loadDailyRecord(key))
        }
        return list
    }

    @Synchronized
    fun addActiveScreenDelta(deltaMs: Long, newCycleMs: Long, config: AppConfig): DailyUsageRecord {
        val key = todayKey()
        val current = loadDailyRecord(key)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
        val nextHourly = current.hourlyMs.copyOf()
        nextHourly[hour] = nextHourly[hour] + deltaMs

        val nextActive = current.activeScreenMs + deltaMs
        val nextMaxContinuous = maxOf(current.maxContinuousMs, newCycleMs)

        val updated = current.copy(
            activeScreenMs = nextActive,
            maxContinuousMs = nextMaxContinuous,
            hourlyMs = nextHourly
        )
        val strain = computeStrainScore(updated, newCycleMs, config)
        val withStrain = updated.copy(avgStrainScore = strain)
        saveDailyRecord(withStrain)
        saveCycleElapsedMs(newCycleMs)
        return withStrain
    }

    @Synchronized
    fun recordBreakCompleted(config: AppConfig): DailyUsageRecord {
        val current = loadDailyRecord(todayKey())
        val updated = current.copy(breaksCompleted = current.breaksCompleted + 1)
        val withStrain = updated.copy(avgStrainScore = computeStrainScore(updated, 0L, config))
        saveDailyRecord(withStrain)
        saveCycleElapsedMs(0L)
        return withStrain
    }

    @Synchronized
    fun recordBreakSkipped(config: AppConfig): DailyUsageRecord {
        val current = loadDailyRecord(todayKey())
        val updated = current.copy(breaksSkipped = current.breaksSkipped + 1)
        val withStrain = updated.copy(avgStrainScore = computeStrainScore(updated, 0L, config))
        saveDailyRecord(withStrain)
        saveCycleElapsedMs(0L)
        return withStrain
    }

    fun computeStrainScore(
        today: DailyUsageRecord,
        cycleElapsedMs: Long,
        config: AppConfig
    ): Int {
        val workMs = (config.workMinutes * 60_000L).coerceAtLeast(60_000L)
        val stretchRatio = cycleElapsedMs.toDouble() / workMs.toDouble()
        val stretchPoints = (stretchRatio * 34.0).toInt().coerceIn(0, 40)

        val todayMinutes = today.activeScreenMs / 60_000.0
        val limit = config.dailyLimitMinutes.coerceAtLeast(60).toDouble()
        val loadPoints = ((todayMinutes / limit) * 28.0).toInt().coerceIn(0, 30)

        val totalBreaks = today.breaksCompleted + today.breaksSkipped
        val skipPenalty = if (totalBreaks > 0) {
            ((today.breaksSkipped.toDouble() / totalBreaks.toDouble()) * 20.0).toInt().coerceIn(0, 20)
        } else 0

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isEvening = hour >= 19 || hour < 6
        val blueLightPenalty = when {
            isEvening && !config.filterEnabled -> 10
            !config.filterEnabled && todayMinutes > 120.0 -> 5
            else -> 0
        }

        return (stretchPoints + loadPoints + skipPenalty + blueLightPenalty).coerceIn(0, 100)
    }

    private fun saveDailyRecord(record: DailyUsageRecord) {
        val map = loadAllRecordsJson()
        val obj = JSONObject().apply {
            put("activeScreenMs", record.activeScreenMs)
            put("breaksCompleted", record.breaksCompleted)
            put("breaksSkipped", record.breaksSkipped)
            put("maxContinuousMs", record.maxContinuousMs)
            put("avgStrainScore", record.avgStrainScore)
            put("hourlyMs", JSONArray().apply {
                record.hourlyMs.forEach { put(it) }
            })
        }
        map.put(record.dateKey, obj)
        prefs.edit().putString("daily_records_json", map.toString()).apply()
    }

    private fun loadAllRecordsJson(): JSONObject {
        val raw = prefs.getString("daily_records_json", null) ?: return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun emptyRecord(dateKey: String): DailyUsageRecord {
        return DailyUsageRecord(
            dateKey = dateKey,
            activeScreenMs = 0L,
            breaksCompleted = 0,
            breaksSkipped = 0,
            maxContinuousMs = 0L,
            hourlyMs = LongArray(24) { 0L },
            avgStrainScore = 0
        )
    }
}
