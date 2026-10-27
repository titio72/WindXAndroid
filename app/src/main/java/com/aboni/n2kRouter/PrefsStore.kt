package com.aboni.n2kRouter

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Create an instance of the datastore at the top level of your kotlin file
val Context.myDataStore by preferencesDataStore(name = "user_prefs")

class PrefsStore(private val context: Context) {

    companion object {
        val CONNECTED_DEVICE = stringPreferencesKey("connected_device")
        val CALIB_PRECISION_PROGRESS = intPreferencesKey("calib_precision_progress")
    }

    suspend fun storeCalibPrecisionProgress(progress: Int) {
        context.myDataStore.edit {
            it[CALIB_PRECISION_PROGRESS] = progress
        }
    }

    /** Read only to carry the device chosen before marine-kit over to the link's device store. */
    val connectedDevice: Flow<String> = context.myDataStore.data.map {
        it[CONNECTED_DEVICE] ?: ""
    }

    fun calibPrecisionProgress(defaultValue: Int): Flow<Int> = context.myDataStore.data.map {
        it[CALIB_PRECISION_PROGRESS] ?: defaultValue
    }

}