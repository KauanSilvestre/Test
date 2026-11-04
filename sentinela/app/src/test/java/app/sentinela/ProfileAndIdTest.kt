package app.sentinela

import app.sentinela.core.model.CameraLens
import app.sentinela.core.model.ProfileKind
import app.sentinela.core.model.RecordingProfile
import app.sentinela.core.model.SessionIds
import app.sentinela.core.model.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Random

class ProfileAndIdTest {

    @Test
    fun `audio profile requests only the microphone`() {
        val profile = RecordingProfile.defaultFor(ProfileKind.AUDIO)
        assertEquals(setOf(SourceKind.MICROPHONE), profile.requestedSources())
    }

    @Test
    fun `camera audio profile requests mic and the chosen lens`() {
        val rear = RecordingProfile(ProfileKind.CAMERA_AUDIO, cameraLens = CameraLens.REAR)
        assertEquals(setOf(SourceKind.MICROPHONE, SourceKind.REAR_CAMERA), rear.requestedSources())

        val both = RecordingProfile(ProfileKind.CAMERA_AUDIO, cameraLens = CameraLens.BOTH)
        assertEquals(
            setOf(SourceKind.MICROPHONE, SourceKind.REAR_CAMERA, SourceKind.FRONT_CAMERA),
            both.requestedSources(),
        )
    }

    @Test
    fun `screen profile needs consent and includes the screen source`() {
        val profile = RecordingProfile.defaultFor(ProfileKind.SCREEN_AUDIO)
        assertTrue(profile.needsScreenConsent)
        assertTrue(SourceKind.SCREEN in profile.requestedSources())
    }

    @Test
    fun `playback audio is only requested when explicitly enabled`() {
        val off = RecordingProfile(ProfileKind.COMPLETE, capturePlaybackAudio = false)
        assertFalse(SourceKind.PLAYBACK_AUDIO in off.requestedSources())
        val on = RecordingProfile(ProfileKind.COMPLETE, capturePlaybackAudio = true)
        assertTrue(SourceKind.PLAYBACK_AUDIO in on.requestedSources())
    }

    @Test
    fun `segment duration is clamped to a sane range`() {
        assertEquals(60_000L, RecordingProfile(ProfileKind.AUDIO, segmentMinutes = 0).segmentDurationMillis)
        assertEquals(
            RecordingProfile.MAX_SEGMENT_MINUTES * 60_000L,
            RecordingProfile(ProfileKind.AUDIO, segmentMinutes = 999).segmentDurationMillis,
        )
    }

    @Test
    fun `generated id matches the expected shape and is valid`() {
        val fixed = Instant.parse("2026-11-03T21:14:18Z")
        val id = SessionIds.newId(fixed, Random(42))
        assertTrue(id.startsWith("2026-11-03T21-14-18Z_"))
        assertTrue(SessionIds.isValid(id))
    }

    @Test
    fun `invalid ids are rejected`() {
        assertFalse(SessionIds.isValid("../escape"))
        assertFalse(SessionIds.isValid("random-folder"))
        assertFalse(SessionIds.isValid(""))
    }
}
