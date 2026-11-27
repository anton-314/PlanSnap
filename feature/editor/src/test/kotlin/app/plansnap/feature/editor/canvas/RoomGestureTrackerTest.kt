package app.plansnap.feature.editor.canvas

import app.plansnap.core.geometry.primitives.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class RoomGestureTrackerTest {
    @Test
    fun `given a finger when it moves then the translation is in world millimeters with y up`() {
        val tracker = RoomGestureTracker(pixelsPerMm = 0.5)
        tracker.move(1, 100.0, 100.0, 0.0)
        tracker.move(1, 150.0, 80.0, 0.0)

        val update = tracker.update()
        assertEquals(100.0, update.translationMm.x, 1e-9)
        assertEquals(40.0, update.translationMm.y, 1e-9)
        assertEquals(0.0, update.rotationRad, 0.0)
    }

    @Test
    fun `given two fingers when they twist clockwise on the screen then the world rotation is negative`() {
        val tracker = RoomGestureTracker(1.0)
        tracker.move(2, 0.0, 0.0, 0.0)
        tracker.move(2, 0.0, 0.0, PI / 4.0)

        assertEquals(-PI / 4.0, tracker.update().rotationRad, 1e-12)
    }

    @Test
    fun `given a twist across the angle wrap when it continues then the rotation stays continuous`() {
        val tracker = RoomGestureTracker(1.0)
        tracker.move(2, 0.0, 0.0, PI - 0.1)
        tracker.move(2, 0.0, 0.0, -PI + 0.1)

        assertEquals(-0.2, tracker.update().rotationRad, 1e-12)
    }

    @Test
    fun `given a second finger when it arrives then the room does not jump`() {
        val tracker = RoomGestureTracker(1.0)
        tracker.move(1, 100.0, 100.0, 0.0)
        tracker.move(1, 110.0, 100.0, 0.0)
        tracker.move(2, 200.0, 150.0, 1.0)

        val update = tracker.update()
        assertEquals(10.0, update.translationMm.x, 1e-9)
        assertEquals(0.0, update.translationMm.y, 1e-9)
        assertEquals(0.0, update.rotationRad, 0.0)
    }

    @Test
    fun `given a finger lifted when the other keeps moving then it continues from its place`() {
        val tracker = RoomGestureTracker(1.0)
        tracker.move(2, 100.0, 100.0, 0.0)
        tracker.move(1, 300.0, 300.0, 0.0)
        tracker.move(1, 310.0, 300.0, 0.0)

        assertEquals(10.0, tracker.update().translationMm.x, 1e-9)
    }

    @Test
    fun `given no finger when fed then nothing changes`() {
        val tracker = RoomGestureTracker(1.0)
        tracker.move(1, 10.0, 10.0, 0.0)
        tracker.move(0, 0.0, 0.0, 0.0)

        assertEquals(Vec2(0.0, 0.0), tracker.update().translationMm)
    }

    @Test
    fun `given a resting finger when it jitters then the slop is not exceeded`() {
        val tracker = RoomGestureTracker(1.0)
        tracker.move(1, 100.0, 100.0, 0.0)
        tracker.move(1, 105.0, 105.0, 0.0)
        assertFalse(tracker.exceeds(10f))

        tracker.move(1, 120.0, 105.0, 0.0)
        assertTrue(tracker.exceeds(10f))
    }

    @Test
    fun `given two fingers when they twist beyond three degrees then the slop is exceeded`() {
        val tracker = RoomGestureTracker(1.0)
        tracker.move(2, 0.0, 0.0, 0.0)
        tracker.move(2, 0.0, 0.0, 0.04)
        assertFalse(tracker.exceeds(10f))

        tracker.move(2, 0.0, 0.0, 0.06)
        assertTrue(tracker.exceeds(10f))
    }
}
