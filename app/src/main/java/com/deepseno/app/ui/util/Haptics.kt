package com.enmooy.deepseno.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Lightweight wrapper over the system Vibrator for tactile feedback on key actions
 * (record start/stop, send, bookmark). Use HapticFeedback inside Composables when
 * available; this helper is for ViewModels / non-Compose callers.
 */
object Haptics {

    private fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun light(context: Context) = pulse(context, 20)
    fun medium(context: Context) = pulse(context, 35)

    private fun pulse(context: Context, durationMs: Long) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        } catch (_: Throwable) {
            // Vibration is best-effort; never crash on hardware quirks.
        }
    }
}
