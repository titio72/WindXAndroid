package com.aboni.n2kRouter

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.runtime.structuralEqualityPolicy
import kotlin.math.roundToInt

private const val STATE_NORMAL = 0
private const val STATE_WAITING = 1
private const val STATE_CALIBRATING = 2

class N2KCalibView(context: Context, ble: BLEThing?) : N2KCardPage(context, ble) {

    constructor(context: Context): this(context, null)

    private val localDataCache = Data()

    private val windDirTxtView: TextView
        get() = findViewById(R.id.txtWindDir)
    private val windSpeedTxtView: TextView
        get() = findViewById(R.id.txtWindSpeed)
    private val calibViewSin: CalibView
        get() = findViewById(R.id.calibSinView)
    private val calibViewCos: CalibView
        get() = findViewById(R.id.calibCosView)
    private val calibSinLowTxt: TextView
        get() = findViewById(R.id.txtCalibSinLow)
    private val calibSinHighTxt: TextView
        get() = findViewById(R.id.txtCalibSinHigh)
    private val calibCosLowTxt: TextView
        get() = findViewById(R.id.txtCalibCosLow)
    private val calibCosHighTxt: TextView
        get() = findViewById(R.id.txtCalibCosHigh)
    private val heapTxt: TextView
        get() = findViewById(R.id.txtHeap)
    private val offsetTxt: TextView
        get() = findViewById(R.id.txtOffset)
    private val angleErrorTxt: TextView
        get() = findViewById(R.id.txtAngleError)
    private val precisionBar: ProgressBar
        get() = findViewById(R.id.precisionBar)
    private val speedAdjTxt: TextView
        get() = findViewById(R.id.txtSpeedAdj)
    private val cosTxt: TextView
        get() = findViewById(R.id.txtCos)
    private val sinTxt: TextView
        get() = findViewById(R.id.txtSin)
    private val btnCalibrate: TextView
        get() = findViewById(R.id.btnCalibrate)
    private val btnCancelCalibrate: TextView
        get() = findViewById(R.id.btnCancelCalibration)
    private val btnCommitCalibrate: TextView
        get() = findViewById(R.id.btnCommitCalibration)
    private val calibProgress: CalibProgressView
        get() = findViewById(R.id.calibProgressView)

    private var state = STATE_NORMAL
        set(value) {
            field = value
            this.post {
                when (value) {
                    STATE_NORMAL -> {
                        btnCalibrate.isEnabled = true
                        btnCancelCalibrate.isEnabled = false
                        btnCommitCalibrate.isEnabled = false
                    }

                    STATE_WAITING -> {
                        btnCalibrate.isEnabled = false
                        btnCancelCalibrate.isEnabled = false
                        btnCommitCalibrate.isEnabled = false
                    }

                    STATE_CALIBRATING -> {
                        btnCalibrate.isEnabled = false
                        btnCancelCalibrate.isEnabled = true
                        btnCommitCalibrate.isEnabled = true
                    }
                }
                btnCalibrate.invalidate()
                btnCommitCalibrate.invalidate()
                btnCancelCalibrate.invalidate()
            }
        }

    init {
        initView()
        attachCard(R.layout.calib_view)
        setTitleResource(R.string.calibration_card_title)
        setImageResource(android.R.drawable.ic_menu_manage)

        findViewById<View>(R.id.btnCosLowDec).setOnClickListener { v -> calibClick(v) }
        findViewById<View>(R.id.btnCosLowInc).setOnClickListener { v -> calibClick(v) }
        findViewById<View>(R.id.btnCosHighDec).setOnClickListener { v -> calibClick(v) }
        findViewById<View>(R.id.btnCosHighInc).setOnClickListener { v -> calibClick(v) }
        findViewById<View>(R.id.btnSinLowDec).setOnClickListener { v -> calibClick(v) }
        findViewById<View>(R.id.btnSinLowInc).setOnClickListener { v -> calibClick(v) }
        findViewById<View>(R.id.btnSinHighDec).setOnClickListener { v -> calibClick(v) }
        findViewById<View>(R.id.btnSinHighInc).setOnClickListener { v -> calibClick(v) }

        findViewById<View>(R.id.btnOffsetDec).setOnClickListener { v -> offsetClick(v) }
        findViewById<View>(R.id.btnOffsetInc).setOnClickListener { v -> offsetClick(v) }

        findViewById<View>(R.id.btnSpeedAdjDec).setOnClickListener { v -> speedAdjClick(v) }
        findViewById<View>(R.id.btnSpeedAdjInc).setOnClickListener { v -> speedAdjClick(v) }

        btnCalibrate.setOnClickListener { _ -> onCalibrateClick() }
        btnCancelCalibrate.setOnClickListener { _ -> onCancelCalibrationClick() }
        btnCommitCalibrate.setOnClickListener { _ -> onCommitCalibrationClick() }

        calibViewSin.max = 4095
        calibViewCos.max = 4095

        state = STATE_NORMAL
    }

    private fun adjustCalLowValue(v: Long): Int {
        return if (v<0 || v>4095) 0 else v.toInt()
    }

    private fun adjustCalHighValue(v: Long): Int {
        return if (v<0 || v>4095) 4095 else v.toInt()
    }

    private var increment: Long
        get() = precisionBar.progress.toLong()
        set(value) { if (value in 0..10) precisionBar.progress = value.toInt() }

    fun speedAdjClick(view: View) {
        var sa = localDataCache.speedAdjustment.value
        if (view.id==R.id.btnSpeedAdjDec) sa -= increment
        else if (view.id==R.id.btnSpeedAdjInc) sa += increment
        if (sa>255) sa = 255 else if (sa<0) sa = 0
        ble?.postSpeedAdjustment(sa.toInt())
    }

    fun offsetClick(view: View) {
        var offset = localDataCache.angleOffset.value
        if (view.id==R.id.btnOffsetDec) offset -= increment
        else if (view.id==R.id.btnOffsetInc) offset += increment
        ble?.postOffset(offset.toInt())
    }

    fun calibClick(view: View) {
        val sinL = adjustCalLowValue(localDataCache.iSinLow.value)
        val sinH = adjustCalHighValue(localDataCache.iSinHigh.value)
        val cosL = adjustCalLowValue(localDataCache.iCosLow.value)
        val cosH = adjustCalHighValue(localDataCache.iCosHigh.value)
        val calib = CalibrationRange(sinL, sinH, cosL, cosH)
        when (view.id) {
            R.id.btnCosLowDec -> { calib.cosLow = adjustCalLowValue(calib.cosLow - increment) }
            R.id.btnCosLowInc -> { calib.cosLow = adjustCalLowValue(calib.cosLow + increment) }
            R.id.btnCosHighDec -> { calib.cosHigh = adjustCalHighValue(calib.cosHigh - increment) }
            R.id.btnCosHighInc -> { calib.cosHigh = adjustCalHighValue(calib.cosHigh + increment) }
            R.id.btnSinLowDec -> { calib.sinLow = adjustCalLowValue(calib.sinLow - increment) }
            R.id.btnSinLowInc -> { calib.sinLow = adjustCalLowValue(calib.sinLow + increment) }
            R.id.btnSinHighDec -> { calib.sinHigh = adjustCalHighValue(calib.sinHigh - increment) }
            R.id.btnSinHighInc -> { calib.sinHigh = adjustCalHighValue(calib.sinHigh + increment) }
        }
        ble?.postCalibration(calib)
    }

    override fun onData(data: Data) {
        post {
            localDataCache.copyFrom(data)

            if (data.iSin.valid) calibViewSin.setAll(
                data.iSinLow.value.toInt(),
                data.iSin.value.toInt(),
                data.iSinHigh.value.toInt()
            )
            if (data.iCos.valid) calibViewCos.setAll(
                data.iCosLow.value.toInt(),
                data.iCos.value.toInt(),
                data.iCosHigh.value.toInt()
            )
            calibSinLowTxt.text = if (data.iSinLow.valid) formatValue(
                context,
                R.string.CALIB_FORMAT,
                data.iSinLow.value
            ) else noValueStr(context)
            calibSinHighTxt.text = if (data.iSinHigh.valid) formatValue(
                context,
                R.string.CALIB_FORMAT,
                data.iSinHigh.value
            ) else noValueStr(context)
            calibCosLowTxt.text = if (data.iCosLow.valid) formatValue(
                context,
                R.string.CALIB_FORMAT,
                data.iCosLow.value
            ) else noValueStr(context)
            calibCosHighTxt.text = if (data.iCosHigh.valid) formatValue(
                context,
                R.string.CALIB_FORMAT,
                data.iCosHigh.value
            ) else noValueStr(context)
            cosTxt.text = if (data.iCos.valid) formatValue(
                context,
                R.string.CALIB_FORMAT,
                data.iCos.value
            ) else noValueStr(context)
            sinTxt.text = if (data.iSin.valid) formatValue(
                context,
                R.string.CALIB_FORMAT,
                data.iSin.value
            ) else noValueStr(context)
            heapTxt.text = if (data.heap.valid) formatValue(
                context,
                R.string.HEAP_FORMAT,
                data.heap.value
            ) else noValueStr(context)
            offsetTxt.text = if (data.angleOffset.valid) formatValue(
                context,
                R.string.WIND_DIR_OFFSET_FORMAT,
                data.angleOffset.value
            ) else noValueStr(context)
            angleErrorTxt.text = if (data.err.valid) formatValue(
                context,
                R.string.CALIB_ELLIPSE_FORMAT,
                data.err.value
            ) else noValueStr(context)
            speedAdjTxt.text = if (data.speedAdjustment.valid) formatValue(
                context,
                R.string.CALIB_ELLIPSE_FORMAT,
                data.speedAdjustment.value / 100.0
            ) else noValueStr(context)
            windDirTxtView.text = if (data.wind.valid) context.getString(R.string.WIND_DIR_FORMAT)
                .format(
                    if (data.wind.value > 180.0) (360.0 - data.wind.value).roundToInt() else data.wind.value.roundToInt(),
                    if (data.wind.value > 180.0) "P" else "S"
                ) else noValueStr(context)
            windSpeedTxtView.text = if (data.speed.valid) formatValue(
                context,
                R.string.WIND_SPEED_FORMAT,
                data.speed.value
            ) else noValueStr(context)

            if (data.isCalibrating) {
                state = STATE_CALIBRATING
                calibProgress.setCalibration(
                    data.calibrationProgress,
                    if (data.wind.valid) data.wind.value else null
                )
            } else {
                state = STATE_NORMAL
                calibProgress.setCalibration(null, if (data.wind.valid) data.wind.value else null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun onCalibrateClick() {
        if (state == STATE_NORMAL) {
            state = STATE_WAITING
            ble?.startCalibration()
        }
    }

    fun onCancelCalibrationClick() {
        if (state == STATE_CALIBRATING) {
            //state = STATE_NORMAL
            ble?.cancelCalibration()
        }
    }

    fun onCommitCalibrationClick() {
        if (state == STATE_CALIBRATING) {
            //state = STATE_NORMAL
            ble?.commitCalibration()
        }
    }
}