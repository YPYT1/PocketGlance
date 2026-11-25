package com.opencapture.pocketglance.session

import android.content.Context
import android.util.Log
import com.opencapture.opencine.dji.DumlFrame
import com.opencapture.opencine.dji.FaceDetectorPort
import com.opencapture.opencine.dji.FaceFrame
import com.opencapture.opencine.dji.PocketSessionHost
import com.opencapture.opencine.dji.StatusExtras
import com.opencapture.opencine.media.FeedIncidentBreadcrumb
import com.opencapture.opencine.media.FeedIncidentRecorder
import com.opencapture.opencine.media.FeedIncidentSessionContext
import com.opencapture.opencine.media.FeedIncidentSink
import com.opencapture.opencine.media.FeedIncidentSnapshot
import com.opencapture.opencine.media.FeedIncidentTestSource
import com.opencapture.opencine.media.FeedRepairRecord
import com.opencapture.opencine.vocabulary.TrackingBox
import com.opencapture.pocketglance.BuildConfig

/** Minimal host: no operator shutter prefs, no face AF, local incident ring only. */
class GlanceSessionHost(context: Context) : PocketSessionHost {
    private val appContext = context.applicationContext
    private val recorder = FeedIncidentRecorder()
    private val sink =
        object : FeedIncidentSink {
            override fun beginSession(context: FeedIncidentSessionContext) {
                recorder.beginSession(context)
            }

            override fun endSession(nowSeconds: Double) {
                recorder.endSession(nowSeconds)
            }

            override fun ingestSnapshot(snapshot: FeedIncidentSnapshot) {
                recorder.recordSnapshot(snapshot)
            }

            override fun recordBreadcrumb(breadcrumb: FeedIncidentBreadcrumb) {
                recorder.recordBreadcrumb(breadcrumb)
            }

            override fun recordRepair(repair: FeedRepairRecord) {
                recorder.recordRepair(repair)
            }

            override fun noteDecoderGeneration(generation: Int) {
                recorder.noteDecoderGeneration(generation)
            }

            override fun noteSocketGeneration(generation: Int) {
                recorder.noteSocketGeneration(generation)
            }

            override fun noteExhausted(nowSeconds: Double) {
                recorder.noteExhausted(nowSeconds)
            }

            override fun noteUnexpectedDisconnect(nowSeconds: Double) {
                recorder.noteUnexpectedDisconnect(nowSeconds)
            }
        }

    override fun shutterUsesAngle(): Boolean = false
    override fun shutterAngleDegrees(): Double = 180.0
    override fun setShutterAngleDegrees(degrees: Double) = Unit
    override fun nativeISOHopEnabled(): Boolean = false

    override fun beginCameraPath(): AutoCloseable = AutoCloseable { }
    override val incidents: FeedIncidentSink get() = sink
    override fun incidentTestSource(): FeedIncidentTestSource = FeedIncidentTestSource.UNKNOWN
    override fun incidentBuildIdentity(): String = "pocketglance-${BuildConfig.VERSION_NAME}"

    override val appVersion: String get() = BuildConfig.VERSION_NAME
    override val appBuild: String get() = BuildConfig.VERSION_CODE.toString()
    override val sourceRevision: String get() = "glance"
    override val debugBuild: Boolean get() = BuildConfig.DEBUG

    private val lastCameraReports = mutableMapOf<String, String>()

    override fun observeCameraFrame(frame: DumlFrame) {
        if (!debugBuild) return
        if (frame.key in FOCUS_REPLIES && frame.flags and 0x80 != 0) {
            val reply = frame.payload.firstOrNull()?.toInt()?.and(0xFF)
            Log.d("GlanceFocus", "reply=${frame.key.toString(16)} status=$reply")
        }
        val item = if (frame.key == 0x0099) StatusExtras.parseSubscribe(frame.payload) else null
        val key = item?.name ?: frame.key.toString(16)
        val value = when {
            item != null && (key.startsWith("cam_photo") || key == "cam_image_effect" || key.contains("live_photo")) ->
                item.value.take(36).joinToString("") { "%02x".format(it) }
            frame.key == 0x0280 -> frame.payload.getOrNull(57)?.let { "%02x".format(it) } ?: return
            else -> return
        }
        if (lastCameraReports.put(key, value) != value) Log.d("GlanceCameraState", "$key $value")
    }

    private companion object {
        val FOCUS_REPLIES = setOf(0x0230, 0x0232, 0x0268, 0x02A6)
    }

    override fun createFaceDetector(): FaceDetectorPort =
        object : FaceDetectorPort {
            override fun wantsFrame(): Boolean = false
            override fun consider(frame: FaceFrame, done: (List<TrackingBox>) -> Unit) {
                frame.release()
                done(emptyList())
            }
            override fun shutdown() = Unit
        }
}
