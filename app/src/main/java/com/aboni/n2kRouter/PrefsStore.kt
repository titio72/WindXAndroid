package com.aboni.n2kRouter

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Create an instance of the datastore at the top level of your kotlin file
val Context.myDataStore by preferencesDataStore(name = "user_prefs")

class PrefsStore(private val context: Context) {



    companion object {
        val CONNECTED_DEVICE = stringPreferencesKey("connected_device")
    }

    suspend fun storeConnectedDevice(device: String) {
        context.myDataStore.edit {
            it[CONNECTED_DEVICE] = device
        }
    }

    val connectedDevice: Flow<String> = context.myDataStore.data.map {
        it[CONNECTED_DEVICE] ?: ""
    }

}