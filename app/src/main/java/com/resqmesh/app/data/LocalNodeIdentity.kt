package com.resqmesh.app.data

import android.content.Context
import java.util.UUID

class LocalNodeIdentityStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getOrCreate(): String {
        val existing = preferences.getString(KEY_NODE_ID, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (existing != null) return existing

        val generated = UUID.randomUUID().toString().replace("-", "").take(32)
        preferences.edit().putString(KEY_NODE_ID, generated).commit()
        return generated
    }

    private companion object {
        const val PREFERENCES_NAME = "resqmesh_local_node_identity"
        const val KEY_NODE_ID = "node_id"
    }
}
