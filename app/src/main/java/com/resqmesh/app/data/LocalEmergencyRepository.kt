package com.resqmesh.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException

class LocalEmergencyRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun list(): List<EmergencyReport> = loadStoredReports()

    fun save(report: EmergencyReport) {
        val reports = EmergencyReportMerge.upsert(loadStoredReports(), report)
        val encoded = JSONArray().apply {
            reports.forEach { put(EmergencyReportJsonCodec.encode(it)) }
        }
        preferences.edit().putString(KEY_REPORTS, encoded.toString()).apply()
    }

    fun updateStatus(reportId: String, status: EmergencyReportStatus): EmergencyReport? {
        val report = loadStoredReports().firstOrNull { it.id == reportId } ?: return null
        val updated = report.copy(status = status)
        save(updated)
        return updated
    }

    private fun loadStoredReports(): List<EmergencyReport> {
        val raw = preferences.getString(KEY_REPORTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    EmergencyReportJsonCodec.decode(array.optJSONObject(index))?.let(::add)
                }
            }.sortedByDescending(EmergencyReport::createdAtMillis)
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "resqmesh_local_emergency_reports"
        const val KEY_REPORTS = "reports"
    }
}
