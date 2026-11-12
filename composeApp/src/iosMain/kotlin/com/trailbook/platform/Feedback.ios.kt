package com.trailbook.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.kSystemSoundID_Vibrate
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

@OptIn(ExperimentalForeignApi::class)
actual class FeedbackPlayer actual constructor() {
    actual fun playSound() {
        AudioServicesPlaySystemSound(1057u)
    }

    actual fun vibrate() {
        UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).impactOccurred()
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
    }
}
