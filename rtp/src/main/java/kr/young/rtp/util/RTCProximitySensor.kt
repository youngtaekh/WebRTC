package kr.young.rtp.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kr.young.common.DebugLog
import org.webrtc.ThreadUtils

class RTCProximitySensor private constructor(
    context: Context,
    sensorStateListener: Runnable
): SensorEventListener {
    private val threadChecker: ThreadUtils.ThreadChecker = ThreadUtils.ThreadChecker()
    private val onSensorStateListener: Runnable = sensorStateListener
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var proximitySensor: Sensor? = null
    private var lastStateReportIsNear = false

    init {
        DebugLog.i(TAG, "RTCProximitySensor ${RTCUtils.getThreadInfo()}")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        threadChecker.checkIsOnValidThread()
        RTCUtils.assertIsTrue(event!!.sensor.type == Sensor.TYPE_PROXIMITY)
        val distanceInCentimeters = event.values[0]
        if (distanceInCentimeters < proximitySensor!!.maximumRange) {
            DebugLog.d(TAG, "Proximity sensor => NEAR state")
            lastStateReportIsNear = true
        } else {
            DebugLog.d(TAG, "Proximity sensor => FAR state")
            lastStateReportIsNear = false
        }

        onSensorStateListener.run()

        DebugLog.d(TAG, "onSensorChanged ${RTCUtils.getThreadInfo()}: " +
                "accuracy=${event.accuracy}, timestamp=${event.timestamp}, " +
                "distance=${event.values[0]}")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        threadChecker.checkIsOnValidThread()
        RTCUtils.assertIsTrue(sensor!!.type == Sensor.TYPE_PROXIMITY)
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            DebugLog.e(TAG, "The values returned by this sensor cannot be trusted")
        }
    }

    fun start(): Boolean {
        threadChecker.checkIsOnValidThread()
        DebugLog.d(TAG, "start ${RTCUtils.getThreadInfo()}")
        if (!initDefaultSensor()) {
            return false
        }
        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        return true
    }

    fun stop() {
        threadChecker.checkIsOnValidThread()
        DebugLog.d(TAG, "stop ${RTCUtils.getThreadInfo()}")
        if (proximitySensor == null) return
        sensorManager.unregisterListener(this, proximitySensor)
    }

    fun sensorReportsNearState(): Boolean {
        threadChecker.checkIsOnValidThread()
        return lastStateReportIsNear
    }

    private fun initDefaultSensor(): Boolean {
        if (proximitySensor != null) {
            return true
        }
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proximitySensor == null) {
            return false
        }
        logProximitySensorInfo()
        return true
    }

    private fun logProximitySensorInfo() {
        if (proximitySensor == null) return

        DebugLog.d(TAG, "Proximity sensor: name=${proximitySensor!!.name}, " +
                "vendor: ${proximitySensor!!.vendor}, " +
                "power: ${proximitySensor!!.power}, " +
                "resolution: ${proximitySensor!!.resolution}, " +
                "max range: ${proximitySensor!!.maximumRange}, " +
                "min delay: ${proximitySensor!!.minDelay}, " +
                "type: ${proximitySensor!!.stringType}, " +
                "max delay: ${proximitySensor!!.maxDelay}, " +
                "reporting mode: ${proximitySensor!!.reportingMode}, " +
                "isWakeUpSensor: ${proximitySensor!!.isWakeUpSensor}")
    }

    companion object {
        private const val TAG = "RTCProximitySensor"

        @JvmStatic
        fun create(context: Context, sensorStateListener: Runnable): RTCProximitySensor {
            return RTCProximitySensor(context, sensorStateListener)
        }
    }
}
