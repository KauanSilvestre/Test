package app.sentinela

import app.sentinela.core.model.SessionOutcome
import app.sentinela.core.model.SessionStatus
import app.sentinela.core.model.SourceKind
import app.sentinela.core.model.SourceReport
import app.sentinela.core.model.SourceState
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionOutcomeTest {

    private fun report(state: SourceState, everActive: Boolean = false, interruptions: Int = 0) =
        SourceReport(state = state, everActive = everActive, interruptions = interruptions)

    @Test
    fun `all requested sources stopped cleanly is COMPLETE`() {
        val sources = mapOf(
            SourceKind.MICROPHONE to report(SourceState.STOPPED, everActive = true),
            SourceKind.REAR_CAMERA to report(SourceState.STOPPED, everActive = true),
        )
        assertEquals(SessionStatus.COMPLETE, SessionOutcome.finalStatus(sources))
    }

    @Test
    fun `one source failing while another records is PARTIAL_SUCCESS`() {
        val sources = mapOf(
            SourceKind.MICROPHONE to report(SourceState.STOPPED, everActive = true),
            SourceKind.SCREEN to report(SourceState.USER_DENIED),
        )
        assertEquals(SessionStatus.PARTIAL_SUCCESS, SessionOutcome.finalStatus(sources))
    }

    @Test
    fun `an interruption downgrades COMPLETE to PARTIAL_SUCCESS`() {
        val sources = mapOf(
            SourceKind.MICROPHONE to report(SourceState.STOPPED, everActive = true, interruptions = 1),
        )
        assertEquals(SessionStatus.PARTIAL_SUCCESS, SessionOutcome.finalStatus(sources))
    }

    @Test
    fun `nothing recorded is FAILED`() {
        val sources = mapOf(
            SourceKind.MICROPHONE to report(SourceState.PERMISSION_DENIED),
            SourceKind.REAR_CAMERA to report(SourceState.UNSUPPORTED),
        )
        assertEquals(SessionStatus.FAILED, SessionOutcome.finalStatus(sources))
    }

    @Test
    fun `no requested sources at all is FAILED`() {
        assertEquals(SessionStatus.FAILED, SessionOutcome.finalStatus(emptyMap()))
    }

    @Test
    fun `active source becomes stopped on finish`() {
        val stopped = SessionOutcome.stoppedState(report(SourceState.ACTIVE, everActive = true))
        assertEquals(SourceState.STOPPED, stopped.state)
    }

    @Test
    fun `source still starting on finish is marked failed`() {
        val stopped = SessionOutcome.stoppedState(report(SourceState.STARTING))
        assertEquals(SourceState.FAILED, stopped.state)
    }

    @Test
    fun `feedback is ALL_ACTIVE when every immediate source is active`() {
        val sources = mapOf(
            SourceKind.MICROPHONE to report(SourceState.ACTIVE, everActive = true),
            SourceKind.REAR_CAMERA to report(SourceState.ACTIVE, everActive = true),
        )
        assertEquals(
            SessionOutcome.StartFeedback.ALL_ACTIVE,
            SessionOutcome.startFeedback(sources, setOf(SourceKind.MICROPHONE, SourceKind.REAR_CAMERA)),
        )
    }

    @Test
    fun `feedback is PARTIAL when some immediate source failed`() {
        val sources = mapOf(
            SourceKind.MICROPHONE to report(SourceState.ACTIVE, everActive = true),
            SourceKind.REAR_CAMERA to report(SourceState.FAILED),
        )
        assertEquals(
            SessionOutcome.StartFeedback.PARTIAL,
            SessionOutcome.startFeedback(sources, setOf(SourceKind.MICROPHONE, SourceKind.REAR_CAMERA)),
        )
    }

    @Test
    fun `feedback is NOTHING_ACTIVE when no immediate source started`() {
        val sources = mapOf(SourceKind.MICROPHONE to report(SourceState.FAILED))
        assertEquals(
            SessionOutcome.StartFeedback.NOTHING_ACTIVE,
            SessionOutcome.startFeedback(sources, setOf(SourceKind.MICROPHONE)),
        )
    }
}
