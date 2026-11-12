package com.trailbook.platform

import android.content.Context

object AndroidContext {
    lateinit var context: Context
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
    }
}
