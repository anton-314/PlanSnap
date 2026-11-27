package app.plansnap.feature.editor.rooms

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.units.Length
import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.canvas.PlanTap
import app.plansnap.feature.editor.state.CoreIntent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** Docking against the real view model, domain and evaluator: the dock sheet, the placement and the undo step. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomsDockTest {
    private var previous: Locale = Locale.getDefault()

    @Before
    fun useEnglishNumbers() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previous)
    }

    private fun TestScope.opened(): RoomsRig = RoomsRig(this).also { it.send(CoreIntent.SelectRoom(GUEST_ID)) }

    private fun RoomsRig.chooseHostWall(openingId: OpeningId? = null) {
        send(
            RoomsIntent.OpenDock,
            RoomsIntent.SelectHost(HOST_ID, WallId("host0"), openingId),
            RoomsIntent.SelectOwnWall(WallId("guest0")),
        )
    }

    /** The interval `[min, max]` the guest occupies along the host wall, which lies on the x axis. */
    private fun RoomsRig.guestSpanAlongX(): Pair<Double, Double> {
        val xs = corners(GUEST_ID).map(Vec2::x)
        return xs.min() to xs.max()
    }

    @Test
    fun `given a selected room when the dock sheet opens then the offset is zero and the only host is chosen`() =
        runTest {
            val rig = opened()
            rig.send(RoomsIntent.OpenDock)
            val draft = checkNotNull(rig.rooms.dock)
            assertEquals(GUEST_ID, draft.guestId)
            assertEquals(HOST_ID, draft.hostRoomId)
            assertEquals("0", draft.offsetText)
            assertFalse(draft.offsetNegative)
            assertFalse(draft.canConfirm)
        }

    @Test
    fun `given a single room when the dock sheet is asked for then it does not open`() =
        runTest {
            val rig = RoomsRig(this, twoRoomProject().let { it.copy(rooms = it.rooms.take(1).toImmutableList()) })
            rig.send(RoomsIntent.OpenDock)
            assertNull(rig.rooms.dock)
        }

    @Test
    fun `given the start of the host wall when docked with offset zero then the guest is flush with it`() =
        runTest {
            val rig = opened()
            rig.chooseHostWall()
            assertTrue(checkNotNull(rig.rooms.dock).canConfirm)
            rig.send(RoomsIntent.ConfirmDock)

            val link = checkNotNull(rig.room(GUEST_ID).placement.dock)
            assertEquals(DockLink(HOST_ID, WallId("host0"), WallId("guest0"), Length.ZERO, null), link)
            val (from, to) = rig.guestSpanAlongX()
            assertEquals(0.0, from, 1e-6)
            assertEquals(2000.0, to, 1e-6)
            // The host wall lies on y = 0 with the host above it, so the guest sits one wall thickness below.
            assertEquals(-120.0, rig.corners(GUEST_ID).maxOf(Vec2::y), 1e-6)
            assertNull("the sheet is closed", rig.rooms.dock)
        }

    @Test
    fun `given an offset when docked then the guest moves along the measuring direction of the host wall`() =
        runTest {
            val rig = opened()
            rig.chooseHostWall()
            rig.send(RoomsIntent.OffsetChanged("50"), RoomsIntent.ConfirmDock)

            assertEquals(
                Length(500),
                rig
                    .room(GUEST_ID)
                    .placement.dock
                    ?.hostOffset,
            )
            val (from, to) = rig.guestSpanAlongX()
            assertEquals(500.0, from, 1e-6)
            assertEquals(2500.0, to, 1e-6)
        }

    @Test
    fun `given a negative offset when docked then the guest moves against the measuring direction`() =
        runTest {
            val rig = opened()
            rig.chooseHostWall()
            rig.send(RoomsIntent.OffsetChanged("30"), RoomsIntent.OffsetNegative(true), RoomsIntent.ConfirmDock)

            assertEquals(
                Length(-300),
                rig
                    .room(GUEST_ID)
                    .placement.dock
                    ?.hostOffset,
            )
            val (from, to) = rig.guestSpanAlongX()
            assertEquals(-300.0, from, 1e-6)
            assertEquals(1700.0, to, 1e-6)
        }

    @Test
    fun `given an opening when docked with offset zero then the guest starts flush with the opening`() =
        runTest {
            val rig = opened()
            rig.chooseHostWall(DOOR_ID)
            rig.send(RoomsIntent.ConfirmDock)

            val link = checkNotNull(rig.room(GUEST_ID).placement.dock)
            assertEquals(DOOR_ID, link.hostOpeningId)
            val (from, to) = rig.guestSpanAlongX()
            assertEquals(500.0, from, 1e-6)
            assertEquals(2500.0, to, 1e-6)
        }

    @Test
    fun `given an opening and an offset when docked then the offset counts from the start of the opening`() =
        runTest {
            val rig = opened()
            rig.chooseHostWall(DOOR_ID)
            rig.send(RoomsIntent.OffsetChanged("10"), RoomsIntent.ConfirmDock)

            val (from, _) = rig.guestSpanAlongX()
            assertEquals(600.0, from, 1e-6)
        }

    @Test
    fun `given a dock when confirmed then it is exactly one undo step and undo removes the link`() =
        runTest {
            val rig = opened()
            val before = rig.state.undoDepth
            rig.chooseHostWall()
            rig.send(RoomsIntent.ConfirmDock)
            assertEquals(before + 1, rig.state.undoDepth)

            rig.send(CoreIntent.Undo)
            assertEquals(before, rig.state.undoDepth)
            assertNull(rig.room(GUEST_ID).placement.dock)
            assertEquals(6000.0, rig.room(GUEST_ID).placement.originX, 0.0)
        }

    @Test
    fun `given docks in a circle when the second is chosen then the sheet explains it and nothing is stored`() =
        runTest {
            val rig = opened()
            rig.chooseHostWall()
            rig.send(RoomsIntent.ConfirmDock)
            val depth = rig.state.undoDepth

            rig.send(
                CoreIntent.SelectRoom(HOST_ID),
                RoomsIntent.OpenDock,
                RoomsIntent.SelectHost(GUEST_ID, WallId("guest1")),
                RoomsIntent.SelectOwnWall(WallId("host1")),
            )
            val draft = checkNotNull(rig.rooms.dock)
            assertEquals(DomainError.RuleViolation(DomainRule.DOCK_CYCLE), draft.error)
            assertFalse(draft.canConfirm)

            rig.send(RoomsIntent.ConfirmDock)
            assertEquals(depth, rig.state.undoDepth)
            assertNotNull("the sheet stays open for a correction", rig.rooms.dock)
        }

    @Test
    fun `given an opening on another wall when chosen then the sheet reports it`() =
        runTest {
            val rig = opened()
            rig.send(
                RoomsIntent.OpenDock,
                RoomsIntent.SelectHost(HOST_ID, WallId("host1"), DOOR_ID),
                RoomsIntent.SelectOwnWall(WallId("guest0")),
            )
            assertEquals(
                DomainError.RuleViolation(DomainRule.DOCK_OPENING_NOT_ON_WALL),
                checkNotNull(rig.rooms.dock).error,
            )
        }

    @Test
    fun `given an offset that is no number when typed then the sheet cannot confirm`() =
        runTest {
            val rig = opened()
            rig.chooseHostWall()
            rig.send(RoomsIntent.OffsetChanged("1,2,3"))
            assertTrue(checkNotNull(rig.rooms.dock).offsetInvalid)
            assertFalse(checkNotNull(rig.rooms.dock).canConfirm)
            rig.send(RoomsIntent.OffsetChanged("12"))
            assertFalse(checkNotNull(rig.rooms.dock).offsetInvalid)
            assertTrue(checkNotNull(rig.rooms.dock).canConfirm)
        }

    @Test
    fun `given an empty offset when docked then it counts as zero`() =
        runTest {
            val rig = opened()
            rig.chooseHostWall()
            rig.send(RoomsIntent.OffsetChanged(""), RoomsIntent.ConfirmDock)
            assertEquals(
                Length.ZERO,
                rig
                    .room(GUEST_ID)
                    .placement.dock
                    ?.hostOffset,
            )
        }

    @Test
    fun `given another host room when chosen then wall and opening must be chosen again`() =
        runTest {
            val rig = opened()
            rig.chooseHostWall(DOOR_ID)
            rig.send(RoomsIntent.SelectHostRoom(HOST_ID))
            assertEquals(WallId("host0"), checkNotNull(rig.rooms.dock).hostWallId)
            rig.send(RoomsIntent.SelectHostRoom(GUEST_ID))
            val draft = checkNotNull(rig.rooms.dock)
            assertNull(draft.hostWallId)
            assertNull(draft.hostOpeningId)
            assertFalse(draft.canConfirm)
        }

    @Test
    fun `given the sheet when dismissed then nothing is stored`() =
        runTest {
            val rig = opened()
            rig.chooseHostWall()
            rig.send(RoomsIntent.Dismiss)
            assertNull(rig.rooms.dock)
            assertEquals(0, rig.state.undoDepth)
        }

    @Test
    fun `given the selection on the plan when a host wall is tapped then it becomes the host`() =
        runTest {
            val rig = opened()
            rig.send(RoomsIntent.OpenDock, RoomsIntent.StartPicking)
            assertTrue(checkNotNull(rig.rooms.dock).picking)

            val tapWall = roomsTapIntent(tapOn(SceneTarget.Wall(HOST_ID, WallId("host2"))), rig.state)
            assertEquals(RoomsIntent.SelectHost(HOST_ID, WallId("host2"), null), tapWall)
            val tapDoor = roomsTapIntent(tapOn(SceneTarget.Opening(HOST_ID, DOOR_ID)), rig.state)
            assertEquals(RoomsIntent.SelectHost(HOST_ID, WallId("host0"), DOOR_ID), tapDoor)
            rig.send(checkNotNull(tapDoor))
            val draft = checkNotNull(rig.rooms.dock)
            assertFalse("choosing the host ends the selection on the plan", draft.picking)
            assertEquals(DOOR_ID, draft.hostOpeningId)
        }

    @Test
    fun `given the selection on the plan when the own room or the ground is tapped then nothing changes`() =
        runTest {
            val rig = opened()
            rig.send(RoomsIntent.OpenDock, RoomsIntent.StartPicking)
            val own = roomsTapIntent(tapOn(SceneTarget.Wall(GUEST_ID, WallId("guest0"))), rig.state)
            val ground = roomsTapIntent(tapOn(null), rig.state)
            assertEquals(RoomsIntent.PickIgnored, own)
            assertEquals(RoomsIntent.PickIgnored, ground)
            rig.send(checkNotNull(own))
            assertTrue(checkNotNull(rig.rooms.dock).picking)
        }

    @Test
    fun `given no selection on the plan when a tap arrives then it is left to the screen`() =
        runTest {
            val rig = opened()
            assertNull(roomsTapIntent(tapOn(null), rig.state))
            rig.send(RoomsIntent.OpenDock)
            assertNull(roomsTapIntent(tapOn(null), rig.state))
        }

    private fun tapOn(target: SceneTarget?) = PlanTap(Vec2(0.0, 0.0), target)
}
