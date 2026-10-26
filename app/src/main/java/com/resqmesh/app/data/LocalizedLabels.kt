package com.resqmesh.app.data

/**
 * Compatibility helpers for non-UI callers. User-visible text must use the
 * resource-backed composable helpers in [com.resqmesh.app.ui.LocalizedUi].
 */
@Deprecated("Use resource-backed localizedText() in Compose UI")
fun EmergencyType.localizedLabel(@Suppress("UNUSED_PARAMETER") language: LanguagePreference): String = label

@Deprecated("Use resource-backed localizedText() in Compose UI")
fun EmergencySeverity.localizedLabel(@Suppress("UNUSED_PARAMETER") language: LanguagePreference): String = label

@Deprecated("Use resource-backed localizedText() in Compose UI")
fun VolunteerSkill.localizedLabel(@Suppress("UNUSED_PARAMETER") language: LanguagePreference): String = label
