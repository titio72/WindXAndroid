package com.aboni.n2kRouter

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.core.graphics.toColorInt

class N2KSettingsView(context: Context, ble: BLEThing?) : N2KCardPage(context, ble) {

    constructor(context: Context): this(context, null)

    //region widgets
    private val buttonSaveDeviceName: ImageButton
        get() = findViewById(R.id.buttonSaveDeviceName)
    private val switchVaneType: SwitchMaterial
        get() = findViewById(R.id.checkBoxST50_ST60)
    private val switchDebug: SwitchMaterial
        get() = findViewById(R.id.checkBoxEnableUSBDebug)
    private val editDeviceName: EditText
        get() = findViewById(R.id.editDeviceName)
    private val deviceNameTxtView: TextView
        get() = findViewById(R.id.txtDeviceName_Settings)
    //endregion

    val localDataCache = Data()

    init {
        initView()
        attachCard(R.layout.settings_view)
        setTitleResource(R.string.settings_card_title)
        setImageResource(android.R.drawable.ic_menu_edit)

        enableButtons(false)
        buttonSaveDeviceName.setOnClickListener { onSaveDeviceNameClick() }
        switchVaneType.setOnClickListener { vaneTypeClick() }
        switchDebug.setOnClickListener { debugClick() }

    }

    fun vaneTypeClick() {
        ble?.postVaneType(if (localDataCache.vaneType.value==0L) 1 else 0)
    }

    fun debugClick() {
        ble?.postDebugToggle(if (localDataCache.debug.value==0L) 1 else 0)
    }

    private fun enableButtons(enable: Boolean) {
        buttonSaveDeviceName.isEnabled = enable
    }

    override fun onStatus(status: BLELifecycleState, scanning: Boolean) {
        post {
            enableButtons(status == BLELifecycleState.Connected)
            if (status == BLELifecycleState.Connected) {
                editDeviceName.text = ble?.getConnectedDevice()?.name?.toEditable() ?: "".toEditable()
                deviceNameTxtView.text = ble?.getConnectedDevice()?.name?.toEditable() ?: "".toEditable()
            } else {
                editDeviceName.text = "".toEditable()
                deviceNameTxtView.text = "".toEditable()
            }
        }
    }

    override fun onData(data: Data) {
        localDataCache.copyFrom(data)
        post {
            deviceNameTxtView.text = ble?.getConnectedDevice()?.name
            switchDebug.isChecked = localDataCache.debug.valid && localDataCache.debug.value == 1L
            switchVaneType.isChecked = localDataCache.vaneType.valid && localDataCache.vaneType.value == 1L
        }
    }

    override fun onScan(device: DeviceItem) {
       // do nothing
    }

    private fun onSaveDeviceNameClick() {
        val n = editDeviceName.text
        ble?.saveDeviceName(n.toString())
    }

    //region extensions
    private fun String.toEditable(): Editable =  Editable.Factory.getInstance().newEditable(this)
    //endregion
}