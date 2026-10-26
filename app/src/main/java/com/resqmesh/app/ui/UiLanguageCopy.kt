package com.resqmesh.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.resqmesh.app.R
import com.resqmesh.app.data.LanguagePreference

data class UiLanguageCopy(
    val appTagline: String,
    val instantSos: String,
    val coordination: String,
    val volunteer: String,
    val resources: String,
    val safety: String
)

@Composable
fun languageCopy(@Suppress("UNUSED_PARAMETER") language: LanguagePreference): UiLanguageCopy = UiLanguageCopy(
    appTagline = stringResource(R.string.offline_emergency_coordination),
    instantSos = stringResource(R.string.instant_sos),
    coordination = stringResource(R.string.coordination_title),
    volunteer = stringResource(R.string.tab_volunteers),
    resources = stringResource(R.string.tab_resources),
    safety = stringResource(R.string.tab_safety)
)
