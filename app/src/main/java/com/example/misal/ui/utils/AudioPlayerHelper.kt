package com.example.misal.ui.utils

import android.media.MediaPlayer
import java.io.File

class AudioPlayerHelper {
    private var mediaPlayer: MediaPlayer? = null

    fun playAudio(file: File, onCompletion: () -> Unit) {
        stopAudio() // Stop any playing audio
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                onCompletion()
                stopAudio()
            }
            prepare()
            start()
        }
    }

    fun stopAudio() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }
}
