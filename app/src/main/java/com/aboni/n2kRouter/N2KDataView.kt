package com.aboni.n2kRouter

import android.content.Context
import android.widget.TextView
import kotlin.math.roundToInt

class N2KDataView(context: Context, ble: BLEThing?) : N2KCardPage(context, ble) {

    constructor(context: Context): this(context, null)

    private val bigWindTxtView: TextView
        get() = findViewById(R.id.txtBigAngleView)
    private val dialWind: DialView
        get() = findViewById(R.id.dialView)
    private val calibViewSin: CalibView
        get() = findViewById(R.id.calibSinViewRO)
    private val calibViewCos: CalibView
        get() = findViewById(R.id.calibCosViewRO)

    init {
        initView()
        attachCard(R.layout.data_view)
        setTitleResource(R.string.data_card_title)
        setImageResource(android.R.drawable.ic_menu_view)
    }

    override fun onData(data: Data) {
        post {
            if (data.wind.valid) {
                dialWind.angle = data.wind.value.roundToInt()
                dialWind.err = data.err.value.toFloat()
                dialWind.calibration = data.calibrationProgress
                dialWind.invalidate()
                calibViewSin.value = data.iSin.value.toInt()
                calibViewSin.high = data.iSinHigh.value.toInt()
                calibViewSin.low = data.iSinLow.value.toInt()
                calibViewSin.max = 4095
                calibViewSin.invalidate()
                calibViewCos.value = data.iCos.value.toInt()
                calibViewCos.high = data.iCosHigh.value.toInt()
                calibViewCos.low = data.iCosLow.value.toInt()
                calibViewCos.max = 4095
                calibViewCos.invalidate()
            } else {
                dialWind.angle = -1;
                dialWind.invalidate()
            }

            if (data.wind.valid && data.speed.valid) {
                val iA =
                    if (data.wind.value > 180.0) (360.0 - data.wind.value).roundToInt() else data.wind.value.roundToInt()
                val sD = if (data.wind.value > 180.0) "P" else "S"
                bigWindTxtView.text = context.getString(R.string.WIND_SPEED_DIR_OK_FORMAT)
                    .format(iA, sD, data.speed.value)
            } else if (data.wind.valid) {
                val iA =
                    if (data.wind.value > 180.0) (360.0 - data.wind.value).roundToInt() else data.wind.value.roundToInt()
                val sD = if (data.wind.value > 180.0) "P" else "S"
                bigWindTxtView.text =
                    context.getString(R.string.WIND_DIR_SPEED_NO_SPEED_FORMAT).format(iA, sD)
            } else if (data.speed.valid) {
                bigWindTxtView.text = context.getString(R.string.WIND_DIR_SPEED_NO_DIR_FORMAT)
                    .format(data.speed.value)
            } else {
                bigWindTxtView.text = context.getString(R.string.WIND_DIR_SPEED_KO_FORMAT)
            }
        }

    }
}