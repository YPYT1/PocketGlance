package com.opencapture.pocketglance.session

import android.content.Context
import com.opencapture.opencine.dji.FaceDetectorPort
import com.opencapture.opencine.dji.FaceFrame
import com.opencapture.opencine.dji.PocketSessionHost
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
