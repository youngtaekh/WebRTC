package com.young.rtp.pc

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Build
import com.young.rtp.util.DefaultValues
import com.young.rtp.util.DefaultValues.Companion.videoFPS
import com.young.rtp.util.DefaultValues.Companion.videoHeight
import com.young.rtp.util.DefaultValues.Companion.videoWidth
import com.young.rtp.util.RTPLog
import org.webrtc.*

class VideoMedia {
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturerStopped = false

    fun createVideoCapturer(context: Context, isScreen: Boolean, data: Intent?) {
        videoCapturer = when {
            isScreen -> {
                Logging.d(TAG, "Creating capturer using screen.")
                createScreenCapturer(data)
            }
            useCamera2(context) -> {
                Logging.d(TAG, "Creating capturer using camera2 API.")
                createCameraCapturer(Camera2Enumerator(context))
            }
            else -> {
                Logging.d(TAG, "Creating capturer using camera1 API.")
                createCameraCapturer(Camera1Enumerator(true))
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun createScreenCapturer(data: Intent?): VideoCapturer? {
        return if (data==null) {
            RTPLog.e(TAG, "User didn't give permission to capture the screen.")
            null
        } else {
            ScreenCapturerAndroid(
                data,
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        RTPLog.e(TAG, "User revoked permission to capture the screen.")
                    }
                })
        }

    }

    private fun useCamera2(context: Context): Boolean {
        return Camera2Enumerator.isSupported(context) && DefaultValues.useCamera2
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.")
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.")
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.")
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.")
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }

    fun createVideoTrack(
        appContext: Context,
        videoSink: VideoSink,
        factory: PeerConnectionFactory,
        eglBase: EglBase
    ): VideoTrack? {
        if (videoCapturer == null)
            return null
        surfaceTextureHelper = SurfaceTextureHelper.create(THREAD_NAME, eglBase.eglBaseContext)
        videoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
        videoCapturer!!.initialize(surfaceTextureHelper, appContext, videoSource!!.capturerObserver)
        videoCapturer!!.startCapture(videoWidth, videoHeight, videoFPS)

        localVideoTrack = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        localVideoTrack!!.setEnabled(true)
        localVideoTrack!!.addSink(videoSink)
        return localVideoTrack
    }

    fun getRemoteVideoTrack(peerConnection: PeerConnection,
                            remoteSinks: List<VideoSink>) {
        for (transceiver in peerConnection.transceivers) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack) {
                remoteVideoTrack = track
                remoteVideoTrack?.setEnabled(true)
                for (remoteSink in remoteSinks) {
                    remoteVideoTrack?.addSink(remoteSink)
                }
                return
            }
        }
    }

    fun release() {
        RTPLog.d(TAG, "dispose videoCapturer.")
        try {
            videoCapturer?.stopCapture()
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
        videoCapturerStopped = true
        videoCapturer?.dispose()
        videoCapturer = null
        RTPLog.d(TAG, "dispose video source.")
        videoSource?.dispose()
        videoSource = null
        RTPLog.d(TAG, "dispose surfaceTextureHelper.")
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
    }

    fun setEnable(enable: Boolean) {
        localVideoTrack?.setEnabled(enable)
        remoteVideoTrack?.setEnabled(enable)
    }

    fun switchCamera() {
        if (videoCapturer is CameraVideoCapturer) {
            val cameraVideoCapturer: CameraVideoCapturer? = videoCapturer as CameraVideoCapturer
            cameraVideoCapturer?.switchCamera(null)
        }
    }

    fun stopVideoSource() {
        if (!videoCapturerStopped) {
            RTPLog.d(TAG, "Stop video source.")
            try {
                videoCapturer?.stopCapture()
            } catch (e: InterruptedException) {
            }
            videoCapturerStopped = true
        }
    }

    fun startVideoSource() {
        if (videoCapturerStopped) {
            RTPLog.d(TAG, "Restart video source.")
            videoCapturer?.startCapture(videoWidth, videoHeight, videoFPS)
            videoCapturerStopped = false
        }
    }

    fun changeCaptureFormat(width: Int, height: Int, fps: Int) {
        RTPLog.i(TAG, "captureFormatChange(width $width, height $height, fps $fps)")
        videoSource?.adaptOutputFormat(width, height, fps)
    }

    companion object {
        private const val TAG = "Video"
        private const val THREAD_NAME = "threadCapture"
        const val VIDEO_TRACK_ID = "ARDAMSv0"
    }
}