package com.example.pml_uts.util

import android.media.AudioManager
import android.media.ToneGenerator

object NotificationSoundUtil {
    fun playSuccessBeep() {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playErrorBeep() {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneG.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}