package com.resqmesh.app.data

object EmergencyRevisionRules {
    const val INITIAL_REVISION = 1
    const val MAX_REVISION = 1_000_000

    fun isValid(revision: Int): Boolean = revision in INITIAL_REVISION..MAX_REVISION

    fun next(revision: Int): Int =
        require(isValid(revision) && revision < MAX_REVISION) {
            "Emergency content revision cannot be incremented"
        }.let { revision + 1 }
}

fun EmergencyReport.bumpContentRevision(): EmergencyReport = copy(
    contentRevision = EmergencyRevisionRules.next(contentRevision),
    contentTrust = EmergencyContentTrust.LOCAL_ORIGINATED,
    updatedAtMillis = System.currentTimeMillis()
)

fun EmergencyReport.withLifecycleStatus(status: EmergencyLifecycleStatus): EmergencyReport =
    copy(lifecycleStatus = status).bumpContentRevision()
