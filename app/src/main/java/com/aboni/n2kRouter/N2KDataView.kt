package com.aboni.n2kRouter

import android.content.Context
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

class N2KDataView(context: Context, ble: BLEThing?, val appState: ApplicationState?) : N2KCardPage(context, ble) {

    constructor(context: Context): this(context, null, null)

    private val bigAngleTxtView: TextView
        get() = findViewById(R.id.txtBigAngleView)
    private val bigSpeedTxtView: TextView
        get() = findViewById(R.id.txtBigSpeedView)
    private val bigEllipseTxtView: TextView
        get() = findViewById(R.id.txtBigEllipseView)
    private val dialWind: DialView
        get() = findViewById(R.id.dialView)
    private val calibViewSin: CalibView
        get() = findViewById(R.id.calibSinViewRO)
    private val calibViewCos: CalibView
        get() = findViewById(R.id.calibCosViewRO)
    private val speedAlpha: TextView
        get() = findViewById(R.id.txtSpeedSmoothing2)
    private val directionAlpha: TextView
        get() = findViewById(R.id.txtDirectionSmoothing2)
    private val btnSpeedAlphaDec: ImageView
        get() = findViewById(R.id.btnSpeedSmoothingDec2)
    private val btnSpeedAlphaInc: ImageView
        get() = findViewById(R.id.btnSpeedSmoothingInc2)
    private val btnDirectionAlphaDec: ImageView
        get() = findViewById(R.id.btnDirectionSmoothingDec2)
    private val btnDirectionAlphaInc: ImageView
        get() = findViewById(R.id.btnDirectionSmoothingInc2)

    private var cacheData: Data? = null

    init {
        initView()
        attachCard(R.layout.data_view)
        setTitleResource(R.string.data_card_title)
        setImageResource(android.R.drawable.ic_menu_view)

        btnSpeedAlphaDec.setOnClickListener { _ -> speedAlphaClick( -1) }
        btnSpeedAlphaInc.setOnClickListener { _ -> speedAlphaClick(1) }
        btnDirectionAlphaDec.setOnClickListener { _ -> directionAlphaClick(-1) }
        btnDirectionAlphaInc.setOnClickListener { _ -> directionAlphaClick(1) }
    }

    fun speedAlphaClick(inc: Int) {
        if (cacheData!=null && ble!=null && cacheData!!.speedSmoothing.valid)
            ble.postSpeedSmoothing((cacheData!!.speedSmoothing.value + inc).coerceAtMost(50).coerceAtLeast(0).toInt());
    }

    fun directionAlphaClick(inc: Int) {
        if (cacheData!=null && ble!=null && cacheData!!.angleSmoothing.valid)
            ble.postDirectionSmoothing((cacheData!!.angleSmoothing.value + inc).coerceAtMost(50).coerceAtLeast(0).toInt());
    }

    override fun onData(data: Data) {
        (context as MainActivity).runOnUiThread {
            cacheData = data
            if (cacheData!!.wind.valid) {
                dialWind.setAngle(cacheData!!.wind.value.roundToInt(), cacheData!!.windSmooth.value.toInt())
                dialWind.err = cacheData!!.err.value.toFloat()
                dialWind.calibration = cacheData!!.calibrationProgress
                dialWind.invalidate()
                calibViewSin.value = cacheData!!.iSin.value.toInt()
                calibViewSin.high = cacheData!!.iSinHigh.value.toInt()
                calibViewSin.low = cacheData!!.iSinLow.value.toInt()
                calibViewSin.max = 4095
                calibViewSin.invalidate()
                calibViewCos.value = cacheData!!.iCos.value.toInt()
                calibViewCos.high = cacheData!!.iCosHigh.value.toInt()
                calibViewCos.low = cacheData!!.iCosLow.value.toInt()
                calibViewCos.max = 4095
                calibViewCos.invalidate()
            } else {
                dialWind.setAngle(-1, -1)
                dialWind.invalidate()
            }

            if (cacheData!!.speedSmoothing.valid) speedAlpha.text = formatValue(context, R.string.ALPHA_FORMAT, cacheData!!.speedSmoothing.value / 50.0) else speedAlpha.text = noValueStr(context)
            if (cacheData!!.angleSmoothing.valid) directionAlpha.text = formatValue(context, R.string.ALPHA_FORMAT, cacheData!!.angleSmoothing.value / 50.0) else directionAlpha.text = noValueStr(context)


            if (cacheData!!.wind.valid) {
                if (cacheData!!.wind.value > 180.0)
                    bigAngleTxtView.text = context.getString(R.string.WIND_DIR_FORMAT).format((360.0 - cacheData!!.wind.value).roundToInt(), "P")
                else
                    bigAngleTxtView.text = context.getString(R.string.WIND_DIR_FORMAT).format(cacheData!!.wind.value.roundToInt(), "S")
            } else bigAngleTxtView.text = noValueStr(context)

            if (cacheData!!.speed.valid) bigSpeedTxtView.text = formatValue(context, R.string.WIND_SPEED_FORMAT, cacheData!!.speed.value) else bigSpeedTxtView.text = noValueStr(context)
            if (cacheData!!.err.valid) bigEllipseTxtView.text = formatValue(context, R.string.CALIB_ELLIPSE_FORMAT, cacheData!!.err.value) else bigEllipseTxtView.text = noValueStr(context)

        }

    }
}