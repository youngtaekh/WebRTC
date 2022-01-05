package com.young.rtp

import android.media.AudioFormat
import android.os.Environment
import com.young.rtp.util.RTPLog
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.*
import java.util.concurrent.ExecutorService

class RecordedAudioToFileController constructor(
    private val executor: ExecutorService
): JavaAudioDeviceModule.SamplesReadyCallback {
    private val lock = Any()
    private var rawAudioFileOutputStream: OutputStream? = null
    private var isRunning: Boolean = false
    private var fileSizeInBytes: Long = 0L

    fun start(): Boolean {
        RTPLog.i(TAG, "start")
        if (!isExternalStorageWritable()) {
            RTPLog.e(TAG, "Writing to external media is not possible")
            return false
        }
        synchronized(lock) {
            isRunning = true
        }
        return true
    }

    fun stop() {
        RTPLog.i(TAG, "stop")
        synchronized(lock) {
            isRunning = false
            if (rawAudioFileOutputStream != null) {
                try {
                    rawAudioFileOutputStream!!.close()
                } catch (e: IOException) {
                    RTPLog.e(TAG, "Failed to close file with saved input audio: ${e.message}")
                }
                rawAudioFileOutputStream = null
            }
            fileSizeInBytes = 0
        }
    }

    private fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    private fun openRawAudioOutputFile(sampleRate: Int, channelCount: Int) {
        val fileName = Environment.getExternalStorageDirectory().path + File.separator +
                "recorded_audio_16bits_" + sampleRate + "Hz" +
                (if (channelCount == 1) "_mono" else "_stereo") + ".pcm"
        val outputFile = File(fileName)
        try {
            rawAudioFileOutputStream = FileOutputStream(outputFile)
        } catch (e: FileNotFoundException) {
            RTPLog.e(TAG, "Failed to open audio output file: ${e.message}")
            stop()
        }
        RTPLog.d(TAG, "Opened file for recording: $fileName")
    }

    companion object {
        private const val TAG = "RecordedAudioToFile"
        private const val MAX_FILE_SIZE_IN_BYTES = 58348800L
    }

    override fun onWebRtcAudioRecordSamplesReady(samples: JavaAudioDeviceModule.AudioSamples?) {
        if (samples!!.audioFormat != AudioFormat.ENCODING_PCM_16BIT) {
            RTPLog.e(TAG, "Invalid audio format")
            return
        }
        synchronized(lock) {
            if (!isRunning) {
                return
            }
            if (rawAudioFileOutputStream == null) {
                openRawAudioOutputFile(samples.sampleRate, samples.channelCount)
                fileSizeInBytes = 0
            }
        }

        executor.execute {
            if (rawAudioFileOutputStream != null) {
                try {
                    if (fileSizeInBytes < MAX_FILE_SIZE_IN_BYTES) {
                        rawAudioFileOutputStream!!.write(samples.data)
                        fileSizeInBytes += samples.data.size
                    }
                } catch (e: IOException) {
                    RTPLog.e(TAG, "Failed to write audio to file: ${e.message}")
                }
            }
        }
    }
}