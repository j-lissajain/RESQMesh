package com.resqmesh.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException

class LocalEmergencyRepository(context: Context) : EmergencyReportRepository {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun list(): List<EmergencyReport> = synchronized(lock) { loadStoredReports() }

    override fun save(report: EmergencyReport): EmergencyReport? = synchronized(lock) {
        if (!EmergencyRevisionRules.isValid(report.contentRevision)) return@synchronized null
        val reports = EmergencyReportMerge.upsert(loadStoredReports(), report)
        val encoded = JSONArray().apply {
            reports.forEach { put(EmergencyReportJsonCodec.encode(it)) }
        }.toString()
        if (preferences.getString(KEY_REPORTS, null) != encoded) {
            preferences.edit().putString(KEY_REPORTS, encoded).commit()
        }
        loadStoredReports().firstOrNull { it.id == report.id }
    }

    override fun updateStatus(reportId: String, status: EmergencyReportStatus): EmergencyReport? = synchronized(lock) {
        val report = loadStoredReports().firstOrNull { it.id == reportId } ?: return null
        val updated = report.copy(status = status)
        save(updated)
        loadStoredReports().firstOrNull { it.id == reportId }
    }

    private fun loadStoredReports(): List<EmergencyReport> {
        val raw = preferences.getString(KEY_REPORTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    EmergencyReportJsonCodec.decode(array.optJSONObject(index))?.let(::add)
                }
            }.fold(emptyList()) { current, report ->
                EmergencyReportMerge.upsert(current, report)
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "resqmesh_local_emergency_reports"
        const val KEY_REPORTS = "reports"
    }
}
