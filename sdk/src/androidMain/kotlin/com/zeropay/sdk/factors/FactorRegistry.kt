package com.zeropay.sdk.factors

import android.content.Context
import android.view.InputDevice
import com.zeropay.sdk.Factor

object FactorRegistry {

    fun availableFactors(context: Context): List<Factor> {
        val list = mutableListOf<Factor>()

        // Always available factors
        list += Factor.COLOUR
        list += Factor.EMOJI
        list += Factor.PIN
        list += Factor.VOICE
        list += Factor.WORDS
        list += Factor.IMAGE_TAP  // If pre-approved images available
        list += Factor.RHYTHM_TAP

        // Android hardware-specific factors
        if (context.packageManager.hasSystemFeature("android.hardware.nfc")) {
            list += Factor.NFC
        }
        if (context.packageManager.hasSystemFeature("android.hardware.sensor.accelerometer")) {
            list += Factor.BALANCE
        }
        if (context.packageManager.hasSystemFeature("android.hardware.camera")) {
            list += Factor.FACE
        }

        // Input device specific factors
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
            else -> { // Finger touch or unknown
                list += Factor.PATTERN_MICRO
                list += Factor.PATTERN_NORMAL
            }
        }
        return list
    }

    private fun detectInputDevice(): Int {
        // Check all connected input devices
        val deviceIds = InputDevice.getDeviceIds()
        
        for (deviceId in deviceIds) {
            val device = InputDevice.getDevice(deviceId) ?: continue
            val sources = device.sources
            
            // Check for mouse
            if ((sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
                return InputDevice.SOURCE_MOUSE
            }
            
            // Check for stylus
            if ((sources and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS) {
                return InputDevice.SOURCE_STYLUS
            }
        }
        
        // Default to touchscreen
        return InputDevice.SOURCE_TOUCHSCREEN
    }
}
