package com.example.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object FeedbackHelper {

    fun triggerScanFeedback(
        context: Context,
        vibrate: Boolean,
        playSound: Boolean
    ) {
        if (vibrate) {
            vibrateLight(context)
        }
        if (playSound) {
            playBeep()
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateLight(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                vibrator.vibrate(45L)
            }
        }
    }

    private fun playBeep() {
        runCatching {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            // Release slightly after tone completes on a daemon thread without leaking
            Thread {
                runCatching {
                    Thread.sleep(180L)
                    toneGenerator.release()
                }
            }.start()
        }
    }
}
