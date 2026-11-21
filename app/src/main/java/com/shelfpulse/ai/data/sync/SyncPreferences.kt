package com.shelfpulse.ai.data.sync

import android.content.Context
import android.content.SharedPreferences

class SyncPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSyncConfig(): SyncConfig {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val presetName = prefs.getString(KEY_PRESET, ConnectorPreset.GENERIC_WEBHOOK.name)
            ?: ConnectorPreset.GENERIC_WEBHOOK.name
        val preset = try {
            ConnectorPreset.valueOf(presetName)
        } catch (e: Exception) {
            ConnectorPreset.GENERIC_WEBHOOK
        }
        val endpoint = prefs.getString(KEY_ENDPOINT, "") ?: ""
        val token = prefs.getString(KEY_TOKEN, "") ?: ""
        val autoSync = prefs.getBoolean(KEY_AUTO_SYNC, false)

        return SyncConfig(
            enabled = enabled,
            preset = preset,
            endpointUrl = endpoint,
            bearerToken = token,
            autoSyncOnSave = autoSync
        )
    }

    fun saveSyncConfig(config: SyncConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_PRESET, config.preset.name)
            .putString(KEY_ENDPOINT, config.endpointUrl)
            .putString(KEY_TOKEN, config.bearerToken)
            .putBoolean(KEY_AUTO_SYNC, config.autoSyncOnSave)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "shelfpulse_sync_prefs"
        private const val KEY_ENABLED = "sync_enabled"
        private const val KEY_PRESET = "sync_preset"
        private const val KEY_ENDPOINT = "sync_endpoint"
        private const val KEY_TOKEN = "sync_token"
        private const val KEY_AUTO_SYNC = "sync_auto_on_save"
    }
}
