package com.opencapture.pocketglance.connection

import com.opencapture.opencine.vocabulary.ConnectionPhase as DjiPhase
import com.opencapture.opencine.vocabulary.SessionRecoveryUi
import kotlin.test.Test
import kotlin.test.assertEquals

class PocketStateTest {
    @Test
    fun wakeIsAnAttemptNotAnInferredPowerReading() {
        assertEquals(PocketState.Unknown, pocketStateFor(DjiPhase.IDLE, false, SessionRecoveryUi.Idle))
        assertEquals(PocketState.ConnectingBluetooth, pocketStateFor(DjiPhase.CONNECTING_GATT, false, SessionRecoveryUi.Idle))
        assertEquals(PocketState.Waking, pocketStateFor(DjiPhase.PAIRING, false, SessionRecoveryUi.Idle))
        assertEquals(PocketState.Pairing, pocketStateFor(DjiPhase.AWAITING_APPROVAL, false, SessionRecoveryUi.Idle))
    }

    @Test
    fun liveNeedsAPresentedFrameAndRecoveryTakesPrecedenceOverHeldPicture() {
        assertEquals(PocketState.Awake, pocketStateFor(DjiPhase.LIVE, false, SessionRecoveryUi.Idle))
        assertEquals(PocketState.Monitoring, pocketStateFor(DjiPhase.LIVE, true, SessionRecoveryUi.Idle))
        assertEquals(PocketState.Recovering, pocketStateFor(DjiPhase.LIVE, true, SessionRecoveryUi.Retrying(1, 8)))
        assertEquals(PocketState.NeedsRetry, pocketStateFor(DjiPhase.LIVE, true, SessionRecoveryUi.WaitingForOperator(8)))
    }

    @Test
    fun anotherDeviceTakingOverHasItsOwnState() {
        assertEquals(PocketState.TakenOver, pocketStateFor(DjiPhase.LIVE, true, SessionRecoveryUi.TakenOver(null)))
        assertEquals(PocketState.TakenOver, pocketStateFor(DjiPhase.LIVE, false, SessionRecoveryUi.TakenOver(null)))
    }
}
