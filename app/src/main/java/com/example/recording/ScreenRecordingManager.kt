package com.example.recording

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object ScreenRecordingManager {
    private val _recordingState = MutableStateFlow<ScreenRecordingState>(ScreenRecordingState.Idle)
    val recordingState: StateFlow<ScreenRecordingState> = _recordingState.asStateFlow()

    fun updateState(state: ScreenRecordingState) {
        _recordingState.value = state
    }

    fun startService(
        context: Context,
        resultCode: Int,
        data: Intent,
        width: Int,
        height: Int,
        densityDpi: Int
    ) {
        val intent = Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_START
            putExtra(ScreenRecordingService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordingService.EXTRA_DATA, data)
            putExtra(ScreenRecordingService.EXTRA_WIDTH, width)
            putExtra(ScreenRecordingService.EXTRA_HEIGHT, height)
            putExtra(ScreenRecordingService.EXTRA_DENSITY, densityDpi)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopService(context: Context) {
        val intent = Intent(context, ScreenRecordingService::class.java).apply {
            action = ScreenRecordingService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun reset() {
        _recordingState.value = ScreenRecordingState.Idle
    }
}
