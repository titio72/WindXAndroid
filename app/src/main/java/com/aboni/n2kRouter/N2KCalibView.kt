package com.aboni.n2kRouter


import android.content.Context
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import kotlin.math.roundToInt

private val INCREMENTS = arrayOf(1L, 2L, 5L, 10L, 25L, 50L, 100L, 250L)

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
    private val btnCancelCalibrate: TextView
        get() = findViewById(R.id.btnCancelCalibration)
    private val btnCommitCalibrate: TextView
        get() = findViewById(R.id.btnCommitCalibration)
    private val calibProgress: CalibProgressView
        get() = findViewById(R.id.calibProgressView)
    private val switchAutoCal: SwitchCompat
        get() = findViewById(R.id.autoCalibrationSwitch)
    private val calibScoreTxt: TextView
        get() = findViewById(R.id.txtCalibScore)
    private val calibScoreThresholdTxt: TextView
        get() = findViewById(R.id.txtCalibTextScoreThreshold)


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

        findViewById<View>(R.id.btnCalibThresholdDec).setOnClickListener { v -> calibScoreThreshold(v) }
        findViewById<View>(R.id.btnCalibThresholdInc).setOnClickListener { v -> calibScoreThreshold(v) }

        btnCancelCalibrate.setOnClickListener { _ -> onCancelCalibrationClick() }
        btnCommitCalibrate.setOnClickListener { _ -> onCommitCalibrationClick() }

        switchAutoCal.setOnClickListener { _ -> autoCalibClick() }

        calibViewSin.max = 4095
        calibViewCos.max = 4095

        updateCalibrationButtons()
    }

    private fun updateCalibrationButtons() {
        btnCancelCalibrate.isEnabled = true
        btnCommitCalibrate.isEnabled = localDataCache.autoCalibration.value == 0L && localDataCache.calibValid.asBoolean() == true
    }

    private fun adjustCalLowValue(v: Long): Int {
        return if (v<0 || v>4095) 0 else v.toInt()
    }

    private fun adjustCalHighValue(v: Long): Int {
        return if (v<0 || v>4095) 4095 else v.toInt()
    }

    private val increment: Long
        get() = INCREMENTS[precisionBar.progress]

    fun calibScoreThreshold(v: View) {
        var sa = localDataCache.calibrationThreshold.value
        if (v.id==R.id.btnCalibThresholdDec) sa -= increment
        else if (v.id==R.id.btnCalibThresholdInc) sa += increment
        if (sa>100) sa = 100 else if (sa<0) sa = 0
        ble?.postCalibrationScoreThreshold(sa.toInt())
    }
    fun autoCalibClick() {
        ble?.postAutoCalibrationToggle()
    }

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

    fun onCancelCalibrationClick() {
        ble?.cancelCalibration()
    }

    fun onCommitCalibrationClick() {
        ble?.commitCalibration()
    }

    override fun onData(data: Data) {
        (context as MainActivity).runOnUiThread {
            localDataCache.copyFrom(data)

            updateCalibrationButtons()

            switchAutoCal.isChecked = localDataCache.isAutocalibrating

            if (localDataCache.iSin.valid) calibViewSin.setAll(
                localDataCache.iSinLow.value.toInt(),
                localDataCache.iSin.value.toInt(),
                localDataCache.iSinHigh.value.toInt()
            )
            if (localDataCache.iCos.valid) calibViewCos.setAll(
                localDataCache.iCosLow.value.toInt(),
                localDataCache.iCos.value.toInt(),
                localDataCache.iCosHigh.value.toInt()
            )
            calibSinLowTxt.text = if (localDataCache.iSinLow.valid) formatValue(context,
                R.string.CALIB_FORMAT,
                localDataCache.iSinLow.value
            ) else noValueStr(context)
            calibSinHighTxt.text = if (localDataCache.iSinHigh.valid) formatValue(context,
                R.string.CALIB_FORMAT,
                localDataCache.iSinHigh.value
            ) else noValueStr(context)
            calibCosLowTxt.text = if (localDataCache.iCosLow.valid) formatValue(context,
                R.string.CALIB_FORMAT,
                localDataCache.iCosLow.value
            ) else noValueStr(context)
            calibCosHighTxt.text = if (localDataCache.iCosHigh.valid) formatValue(context,
                R.string.CALIB_FORMAT,
                localDataCache.iCosHigh.value
            ) else noValueStr(context)
            cosTxt.text = if (localDataCache.iCos.valid) formatValue(context,
                R.string.CALIB_FORMAT,
                localDataCache.iCos.value
            ) else noValueStr(context)
            sinTxt.text = if (localDataCache.iSin.valid) formatValue(context,
                R.string.CALIB_FORMAT,
                localDataCache.iSin.value
            ) else noValueStr(context)
            offsetTxt.text = if (localDataCache.angleOffset.valid) formatValue(context,
                R.string.WIND_DIR_OFFSET_FORMAT,
                localDataCache.angleOffset.value
            ) else noValueStr(context)
            angleErrorTxt.text = if (localDataCache.err.valid) formatValue(context,
                R.string.CALIB_ELLIPSE_FORMAT,
                localDataCache.err.value
            ) else noValueStr(context)
            speedAdjTxt.text = if (localDataCache.speedAdjustment.valid) formatValue(context,
                R.string.CALIB_ELLIPSE_FORMAT,
                localDataCache.speedAdjustment.value / 100.0
            ) else noValueStr(context)
            windDirTxtView.text = if (localDataCache.wind.valid) context.getString(R.string.WIND_DIR_FORMAT)
                .format(
                    if (localDataCache.wind.value > 180.0) (360.0 - localDataCache.wind.value).roundToInt() else localDataCache.wind.value.roundToInt(),
                    if (localDataCache.wind.value > 180.0) "P" else "S"
                ) else noValueStr(context)
            windSpeedTxtView.text = if (localDataCache.speed.valid) formatValue(context,
                R.string.WIND_SPEED_FORMAT,
                localDataCache.speed.value
            ) else noValueStr(context)
            calibScoreThresholdTxt.text = if (localDataCache.calibrationThreshold.valid) formatValue(context,
                R.string.CALIB_SCORE_FORMAT,
                localDataCache.calibrationThreshold.value
            ) else noValueStr(context)

            if (localDataCache.isCalibrating) {
                calibProgress.setCalibration(
                    localDataCache.calibrationProgress,
                    if (localDataCache.wind.valid) localDataCache.wind.value else null, (if (localDataCache.calibInScore.valid) localDataCache.calibInScore.asBoolean() else false) == true
                )
                calibScoreTxt.text = formatValue(
                    context,
                    R.string.CALIB_SCORE_FORMAT,
                    localDataCache.calibrationProgress.score)
            } else {
                calibProgress.setCalibration(null, if (localDataCache.wind.valid) localDataCache.wind.value else null, false)
                calibScoreTxt.text = noValueStr(context)
            }
        }
    }
}