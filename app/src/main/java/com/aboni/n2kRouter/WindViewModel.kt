package com.aboni.n2kRouter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aboni.marine.link.CommandResult
import com.aboni.marine.link.DEVICE_SCAN_MS
import com.aboni.marine.link.PipeLink
import com.aboni.marine.link.ble.AndroidBleRadio
import com.aboni.marine.link.ble.BlePipe
import com.aboni.marine.link.ble.PrefsDeviceStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val HEARTBEAT_MS = 2_500L

/** Owns the link to the sensor, so it survives configuration changes. */
class WindViewModel(app: Application) : AndroidViewModel(app) {

    private val deviceStore = PrefsDeviceStore(app, "wind_device")
    private val prefs = PrefsStore(app)

    val link = PipeLink(
        viewModelScope,
        BlePipe(viewModelScope, AndroidBleRadio(app), WIND_PROFILE, deviceStore),
        WindProtocol,
    )

    private val _scanning = MutableStateFlow(false)
    /** True while a scan asked for by the user runs. */
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _increment = MutableStateFlow(DEFAULT_INCREMENT_INDEX)
    /** Index into [INCREMENTS] of the step used by the calibration buttons. */
    val increment: StateFlow<Int> = _increment.asStateFlow()

    private var heartbeat: Job? = null
    private var scanTimer: Job? = null

    init {
        viewModelScope.launch {
            _increment.value = prefs.calibPrecisionProgress(DEFAULT_INCREMENT_INDEX).first().coerceIn(INCREMENTS.indices)
            // Before marine-kit the chosen device was kept in DataStore: carry it over once.
            val old = prefs.connectedDevice.first()
            if (old.isNotEmpty() && deviceStore.load() == null) {
                deviceStore.save(old)
                link.select(old)
            }
        }
    }

    /** Connects (and keeps the sensor awake with a heartbeat) while the app is visible. */
    fun start() {
        link.start()
        if (heartbeat?.isActive == true) return
        heartbeat = viewModelScope.launch {
            // The sensor drops the session after a while without commands.
            while (isActive) {
                link.send(WindCommand.Heartbeat)
                delay(HEARTBEAT_MS)
            }
        }
    }

    fun stop() {
        heartbeat?.cancel()
        heartbeat = null
        link.stop()
    }

    fun scan() {
        link.scan()
        scanTimer?.cancel()
        scanTimer = viewModelScope.launch {
            _scanning.value = true
            delay(DEVICE_SCAN_MS)
            _scanning.value = false
        }
    }

    fun setIncrement(index: Int) {
        _increment.value = index
        viewModelScope.launch { prefs.storeCalibPrecisionProgress(index) }
    }

    suspend fun send(cmd: WindCommand): CommandResult = link.send(cmd)

    override fun onCleared() {
        link.close()
    }
}
