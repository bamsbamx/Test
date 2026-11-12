package com.trailbook.platform

import java.awt.Toolkit

actual class FeedbackPlayer actual constructor() {
    actual fun playSound() {
        try {
            Toolkit.getDefaultToolkit().beep()
        } catch (e: Exception) {
        }
    }

    actual fun vibrate() {
    }
}
