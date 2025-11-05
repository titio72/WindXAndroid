package com.aboni.n2kRouter

class CalibrationRange {
    constructor(sL: Int, sH: Int, cL: Int, cH: Int) {
        sinLow = sL
        sinHigh = sH
        cosLow = cL
        cosHigh = cH
    }

    var sinLow: Int = 0
    var sinHigh: Int = 4095
    var cosLow: Int = 0
    var cosHigh: Int = 5095
}