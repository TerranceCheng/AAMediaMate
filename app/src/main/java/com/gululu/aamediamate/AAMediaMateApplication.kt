package com.gululu.aamediamate

import android.app.Application
import com.gululu.aamediamate.diagnostics.DiagnosticLogger

class AAMediaMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagnosticLogger.initialize(this)
    }
}
