package com.example.recording

import java.io.File

sealed class ScreenRecordingState {
    object Idle : ScreenRecordingState()
    data class Recording(val durationSeconds: Long = 0, val tempFile: File? = null) : ScreenRecordingState()
    data class Finished(val recordedFile: File) : ScreenRecordingState()
    data class Error(val message: String) : ScreenRecordingState()
}
