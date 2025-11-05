package com.aboni.n2kRouter

class ApplicationState {

    companion object {
        const val STATE_NORMAL = 0
        const val STATE_WAITING = 1
        const val STATE_CALIBRATING = 2

        const val STATE_ERROR = 3
    }

    public var state = STATE_NORMAL
        set(value) {
            val old = field
            if (old==value) return
            appendLog("Changing state from $old to $value")
            field = value
        }
}