package com.zeropay.sdk.factors

import android.content.Context
import android.view.InputDevice
// We need to import Factor from the parent package
import com.zeropay.sdk.Factor 

object FactorRegistry {

    fun availableFactors(context: Context): List<Factor> {
        val list = mutableListOf<Factor>()

        // always available
        list += Factor.COLOUR
        list += Factor.EMOJI
        list += Factor.PIN
        list += Factor.VOICE

        // Android specific
        if (context.packageManager.hasSystemFeature("android.hardware.nfc")) list += Factor.NFC
        if (context.packageManager.hasSystemFeature("android.hardware.sensor.accelerometer")) list += Factor.BALANCE
        if (context.packageManager.hasSystemFeature("android.hardware.camera")) list += Factor.FACE

        // input device specific
        when (detectInputDevice()) {
            InputDevice.SOURCE_MOUSE -> {
                list += Factor.MOUSE_DRAW
                list += Factor.PATTERN_MICRO
                list += Factor.PATTERN_NORMAL
            }
            InputDevice.SOURCE_STYLUS -> {
                list += Factor.STYLUS_DRAW
                list += Factor.PATTERN_MICRO
                list += Factor.PATTERN_NORMAL
            }
            else -> { // finger touch
                list += Factor.PATTERN_MICRO
                list += Factor.PATTERN_NORMAL
            }
        }
        return list
    }

    private fun detectInputDevice(): Int {
        // simplified: return SOURCE_MOUSE if mouse plugged, else SOURCE_STYLUS if stylus, else touch
        // real implementation uses InputDevice.getDeviceIds()
        return InputDevice.SOURCE_TOUCHSCREEN // default for now
    }
}