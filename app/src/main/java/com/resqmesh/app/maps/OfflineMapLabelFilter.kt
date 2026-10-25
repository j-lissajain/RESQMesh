package com.resqmesh.app.maps

data class MapLabelCandidate(
    val name: String,
    val x: Float,
    val baselineY: Float,
    val width: Float,
    val height: Float,
    val priority: Int
)

object OfflineMapLabelFilter {
    fun select(
        candidates: List<MapLabelCandidate>,
        maxLabels: Int = 80,
        padding: Float = 4f
    ): List<MapLabelCandidate> {
        val accepted = mutableListOf<MapLabelCandidate>()
        val seenNames = mutableSetOf<String>()
        candidates
            .sortedWith(compareByDescending<MapLabelCandidate> { it.priority }.thenBy { it.name })
            .forEach { candidate ->
                if (accepted.size >= maxLabels) return@forEach
                val normalizedName = candidate.name.trim().lowercase()
                if (!seenNames.add(normalizedName)) return@forEach
                val candidateLeft = candidate.x - padding
                val candidateTop = candidate.baselineY - candidate.height - padding
                val candidateRight = candidate.x + candidate.width + padding
                val candidateBottom = candidate.baselineY + padding
                val overlaps = accepted.any { existing ->
                    val existingLeft = existing.x - padding
                    val existingTop = existing.baselineY - existing.height - padding
                    val existingRight = existing.x + existing.width + padding
                    val existingBottom = existing.baselineY + padding
                    candidateLeft < existingRight && candidateRight > existingLeft &&
                        candidateTop < existingBottom && candidateBottom > existingTop
                }
                if (!overlaps) accepted += candidate
            }
        return accepted
    }
}
