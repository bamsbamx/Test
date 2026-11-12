package com.trailbook

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.trailbook.app.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "TrailBook") {
        App()
    }
}
