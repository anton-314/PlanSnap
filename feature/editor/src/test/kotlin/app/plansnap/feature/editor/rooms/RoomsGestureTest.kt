package app.plansnap.feature.editor.rooms

import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.snap.SnapKind
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.canvas.RoomGestureStart
import app.plansnap.feature.editor.canvas.RoomGestureUpdate
import app.plansnap.feature.editor.state.CoreIntent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * The long-press gesture against the real view model: the preview never changes the model, the end of the gesture
 * is exactly one command, and snapping follows the settings at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomsGestureTest {
    private fun start(
        distance: Double = SNAP_MM,
        grid: Double = GRID_MM,
    ) = RoomsIntent.GestureStarted(RoomGestureStart(GUEST_ID, distance, grid))

    private fun move(
        dx: Double,
        dy: Double,
        rotation: Double = 0.0,
    ) = RoomsIntent.GestureMoved(RoomGestureUpdate(Vec2(dx, dy), rotation))

    private fun TestScope.rig(settings: AppSettings = AppSettings(snapEnabled = false)) =
        RoomsRig(this, settings = settings)

    private fun RoomsRig.dockGuestToHost() {
        send(
            CoreIntent.SelectRoom(GUEST_ID),
            RoomsIntent.OpenDock,
            RoomsIntent.SelectHost(HOST_ID, WallId("host0")),
            RoomsIntent.SelectOwnWall(WallId("guest0")),
            RoomsIntent.ConfirmDock,
        )
    }

    private fun RoomsRig.guestLink() = room(GUEST_ID).placement.dock

    @Test
    fun `given a long press on a room when it starts then the room is selected and previewed without a command`() =
        runTest {
            val rig = rig()
            val project = rig.state.project
            rig.send(start())
            assertEquals(GUEST_ID, rig.state.selectedRoomId)
            assertNotNull(rig.rooms.drag)
            assertEquals(setOf(GUEST_ID), rig.rooms.drag?.movingIds)
            assertSame(project, rig.state.project)
            assertEquals(0, rig.state.undoDepth)
        }

    @Test
    fun `given a running gesture when it moves then only the preview changes in every frame`() =
        runTest {
            val rig = rig()
            val project = rig.state.project
            rig.send(start())
            for (step in 1..20) {
                rig.send(move(step * 50.0, step * 10.0))
                assertSame("frame $step must not change the model", project, rig.state.project)
                assertEquals(0, rig.state.undoDepth)
                assertEquals(Vec2(step * 50.0, step * 10.0), rig.rooms.drag?.translation)
            }
        }

    @Test
    fun `given a drag when released then exactly one command moves the room and undo takes it back`() =
        runTest {
            val rig = rig()
            rig.send(start())
            for (step in 1..10) rig.send(move(step * 100.0, -step * 20.0))
            rig.send(RoomsIntent.GestureEnded)

            assertNull(rig.rooms.drag)
            assertEquals(1, rig.state.undoDepth)
            val placement = rig.room(GUEST_ID).placement
            assertEquals(7000.0, placement.originX, 1e-6)
            assertEquals(-200.0, placement.originY, 1e-6)
            assertEquals(0.0, placement.rotationRad, 1e-12)

            rig.send(CoreIntent.Undo)
            assertEquals(0, rig.state.undoDepth)
            assertEquals(6000.0, rig.room(GUEST_ID).placement.originX, 0.0)
            assertEquals(0.0, rig.room(GUEST_ID).placement.originY, 0.0)
        }

    @Test
    fun `given a twist when released then the room is rotated around its center as one undo step`() =
        runTest {
            val rig = rig()
            val before = rig.corners(GUEST_ID)
            rig.send(start())
            val pivot = checkNotNull(rig.rooms.drag).pivot
            val angle = 0.5
            rig.send(move(300.0, 0.0, angle))
            rig.send(RoomsIntent.GestureEnded)

            assertEquals(1, rig.state.undoDepth)
            assertEquals(angle, rig.room(GUEST_ID).placement.rotationRad, 1e-9)
            val after = rig.corners(GUEST_ID)
            before.zip(after).forEach { (b, a) ->
                val dx = b.x - pivot.x
                val dy = b.y - pivot.y
                assertEquals(dx * cos(angle) - dy * sin(angle) + pivot.x + 300.0, a.x, 1e-6)
                assertEquals(dx * sin(angle) + dy * cos(angle) + pivot.y, a.y, 1e-6)
            }
        }

    @Test
    fun `given a docked room when it is moved freely then the dock link is removed and undo restores it`() =
        runTest {
            val rig = rig()
            rig.dockGuestToHost()
            val docked = rig.room(GUEST_ID).placement.dock
            assertEquals(DockLink(HOST_ID, WallId("host0"), WallId("guest0"), Length.ZERO, null), docked)

            rig.send(start(), move(0.0, -1500.0), RoomsIntent.GestureEnded)
            assertNull(rig.room(GUEST_ID).placement.dock)

            rig.send(CoreIntent.Undo)
            assertEquals(docked, rig.room(GUEST_ID).placement.dock)
        }

    @Test
    fun `given a host when it is dragged then the rooms docked to it move with the preview`() =
        runTest {
            val rig = rig()
            rig.dockGuestToHost()
            rig.send(RoomsIntent.GestureStarted(RoomGestureStart(HOST_ID, SNAP_MM, GRID_MM)))
            assertEquals(setOf(HOST_ID, GUEST_ID), rig.rooms.drag?.movingIds)
            val preview = checkNotNull(roomsCanvasPreview(rig.state))
            assertEquals(setOf(HOST_ID, GUEST_ID), preview.roomIds)
        }

    @Test
    fun `given a gesture when it is cancelled then nothing is stored and the preview is gone`() =
        runTest {
            val rig = rig()
            rig.send(start(), move(500.0, 500.0), RoomsIntent.GestureCancelled)
            assertNull(rig.rooms.drag)
            assertNull(roomsCanvasPreview(rig.state))
            assertEquals(0, rig.state.undoDepth)
            assertEquals(6000.0, rig.room(GUEST_ID).placement.originX, 0.0)
        }

    @Test
    fun `given a long press without movement when released then no command is stored`() =
        runTest {
            val rig = rig()
            rig.send(start(), move(0.0, 0.0), RoomsIntent.GestureEnded)
            assertEquals(0, rig.state.undoDepth)
        }

    @Test
    fun `given an open dock sheet when a gesture starts then it is ignored`() =
        runTest {
            val rig = rig()
            rig.send(CoreIntent.SelectRoom(GUEST_ID), RoomsIntent.OpenDock, start())
            assertNull(rig.rooms.drag)
        }

    @Test
    fun `given movement without a gesture when it arrives then it is ignored`() =
        runTest {
            val rig = rig()
            rig.send(move(100.0, 100.0), RoomsIntent.GestureEnded)
            assertEquals(0, rig.state.undoDepth)
        }

    @Test
    fun `given snapping on when a corner comes close to a corner then the room snaps to it`() =
        runTest {
            val rig = rig(AppSettings(snapEnabled = true, gridEnabled = false))
            rig.send(start(), move(-1990.0, 5.0))
            val drag = checkNotNull(rig.rooms.drag)
            assertEquals(SnapKind.CORNER, drag.snap)
            assertEquals(-2000.0, drag.translation.x, 1e-9)
            assertEquals(0.0, drag.translation.y, 1e-9)

            rig.send(RoomsIntent.GestureEnded)
            assertEquals(4000.0, rig.room(GUEST_ID).placement.originX, 1e-6)
            assertEquals(0.0, rig.room(GUEST_ID).placement.originY, 1e-6)
        }

    @Test
    fun `given snapping off when a corner comes close to a corner then the room stays where the finger is`() =
        runTest {
            val rig = rig(AppSettings(snapEnabled = false, gridEnabled = true))
            rig.send(start(), move(-1990.0, 5.0))
            val drag = checkNotNull(rig.rooms.drag)
            assertNull(drag.snap)
            assertEquals(Vec2(-1990.0, 5.0), drag.translation)
        }

    @Test
    fun `given snapping on and the grid on when far from walls then a corner snaps to a grid point`() =
        runTest {
            val rig = rig(AppSettings(snapEnabled = true, gridEnabled = true))
            rig.send(start(), move(1030.0, 2540.0))
            val drag = checkNotNull(rig.rooms.drag)
            assertEquals(SnapKind.GRID, drag.snap)
            assertEquals(1000.0, drag.translation.x, 1e-9)
            assertEquals(2500.0, drag.translation.y, 1e-9)
        }

    @Test
    fun `given the grid off when snapping is on then a corner does not snap to the grid`() =
        runTest {
            val rig = rig(AppSettings(snapEnabled = true, gridEnabled = false))
            rig.send(start(), move(1030.0, 2540.0))
            assertNull(checkNotNull(rig.rooms.drag).snap)
            assertEquals(Vec2(1030.0, 2540.0), rig.rooms.drag?.translation)
        }

    @Test
    fun `given a switch of snapping during a gesture when the next event arrives then it applies at once`() =
        runTest {
            val rig = rig(AppSettings(snapEnabled = true, gridEnabled = true))
            rig.send(start(), move(1030.0, 2540.0))
            assertEquals(SnapKind.GRID, rig.rooms.drag?.snap)

            rig.send(RoomsIntent.SetSnap(false))
            assertEquals(false, rig.state.settings.snapEnabled)
            assertEquals(false, rig.rig.settingsRepository.current.snapEnabled)
            rig.send(move(1030.0, 2540.0))
            assertNull(rig.rooms.drag?.snap)

            rig.send(RoomsIntent.SetSnap(true), RoomsIntent.SetGrid(false), move(1030.0, 2540.0))
            assertNull("grid is off", rig.rooms.drag?.snap)
            assertEquals(false, rig.state.settings.gridEnabled)
        }

    @Test
    fun `given snapping on when twisting then the orientation follows the fingers exactly`() =
        runTest {
            val rig = rig(AppSettings(snapEnabled = true, gridEnabled = false))
            rig.send(start(), move(0.0, 0.0, 0.26))
            assertEquals(0.26, checkNotNull(rig.rooms.drag).rotationRad, 0.0)
            rig.send(RoomsIntent.GestureEnded)
            assertEquals(0.26, rig.room(GUEST_ID).placement.rotationRad, 1e-12)
        }

    @Test
    fun `given a docked room when its host is turned and released then it ends where the preview showed it`() =
        runTest {
            val rig = rig()
            rig.dockGuestToHost()
            val before = rig.corners(GUEST_ID)
            rig.send(RoomsIntent.GestureStarted(RoomGestureStart(HOST_ID, SNAP_MM, GRID_MM)))
            rig.send(move(700.0, -400.0, 0.4))
            val preview = checkNotNull(roomsCanvasPreview(rig.state))
            rig.send(RoomsIntent.GestureEnded)

            assertEquals(DockLink(HOST_ID, WallId("host0"), WallId("guest0"), Length.ZERO, null), rig.guestLink())
            before.zip(rig.corners(GUEST_ID)).forEach { (old, new) ->
                val shown = (old - preview.pivotMm).rotated(0.4) + preview.pivotMm + preview.translationMm
                assertEquals(shown.x, new.x, 1e-6)
                assertEquals(shown.y, new.y, 1e-6)
            }
        }

    @Test
    fun `given a docked room without a shape when its host is dragged then it stays in preview and result`() =
        runTest {
            val project = twoRoomProject()
            val open =
                project.rooms.map {
                    if (it.id != GUEST_ID) {
                        it
                    } else {
                        it.copy(
                            walls = it.walls.take(2).toImmutableList(),
                            corners = it.corners.take(2).toImmutableList(),
                            isClosed = false,
                        )
                    }
                }
            val rig = RoomsRig(this, project.copy(rooms = open.toImmutableList()), AppSettings(snapEnabled = false))
            rig.dockGuestToHost()
            val placement = rig.room(GUEST_ID).placement
            rig.send(RoomsIntent.GestureStarted(RoomGestureStart(HOST_ID, SNAP_MM, GRID_MM)), move(500.0, 0.0))

            assertEquals(setOf(HOST_ID), rig.rooms.drag?.movingIds)
            rig.send(RoomsIntent.GestureEnded)
            assertEquals(placement, rig.room(GUEST_ID).placement)
            assertEquals(500.0, rig.room(HOST_ID).placement.originX, 1e-6)
        }

    @Test
    fun `given snapping on when a corner comes close to a wall then the room snaps onto the wall line`() =
        runTest {
            val rig = rig(AppSettings(snapEnabled = true, gridEnabled = false))
            rig.send(start(), move(-1940.0, 500.0))
            val drag = checkNotNull(rig.rooms.drag)
            assertEquals(SnapKind.WALL, drag.snap)
            assertEquals(-2000.0, drag.translation.x, 1e-9)
            assertEquals(500.0, drag.translation.y, 1e-9)
        }

    @Test
    fun `given empty rooms when more rooms are added then each gets its own place`() =
        runTest {
            val rig = rig()
            rig.send(RoomsIntent.AddRoom)
            rig.send(RoomsIntent.AddRoom)
            val origins = checkNotNull(rig.state.project).rooms.map { it.placement.originX }
            assertTrue("strictly to the right: $origins", origins.zipWithNext().all { (a, b) -> b > a })
        }

    @Test
    fun `given a project of 30 rooms when another is added then it is accepted`() =
        runTest {
            val rig = rig()
            repeat(29) { rig.send(RoomsIntent.AddRoom) }
            assertEquals(31, checkNotNull(rig.state.project).rooms.size)
        }

    @Test
    fun `given a room when a new room is added then it is selected, placed beside the plan and undoable`() =
        runTest {
            val rig = rig()
            rig.send(RoomsIntent.AddRoom)
            val rooms = checkNotNull(rig.state.project).rooms
            assertEquals(3, rooms.size)
            val added = rooms.last()
            assertEquals(added.id, rig.state.selectedRoomId)
            val sceneMaxX =
                rig.state.scene?.rooms?.filter { it.roomId != added.id }?.maxOf {
                    checkNotNull(
                        it.bounds,
                    ).maxX
                }
            assertTrue(added.placement.originX > checkNotNull(sceneMaxX))
            assertEquals(1, rig.state.undoDepth)

            rig.send(CoreIntent.Undo)
            assertEquals(2, checkNotNull(rig.state.project).rooms.size)
        }

    @Test
    fun `given a switch of the grid when sent then the settings change at once`() =
        runTest {
            val rig = rig(AppSettings(gridEnabled = true))
            rig.send(RoomsIntent.SetGrid(false))
            assertEquals(false, rig.state.settings.gridEnabled)
            assertEquals(false, roomsShowGrid(rig.state))
            rig.send(RoomsIntent.SetGrid(true))
            assertEquals(true, roomsShowGrid(rig.state))
        }

    @Test
    fun `given the state when the menu is asked then it lists the entries as data`() =
        runTest {
            val rig = rig(AppSettings(gridEnabled = true, snapEnabled = false))
            rig.send(CoreIntent.SelectRoom(GUEST_ID))
            val items = roomsMenuItems(rig.state)
            assertEquals(
                listOf(
                    RoomsIntent.AddRoom,
                    RoomsIntent.OpenDock,
                    RoomsIntent.SetGrid(false),
                    RoomsIntent.SetSnap(true),
                ),
                items.map { it.intent },
            )
            assertEquals(listOf(null, null, true, false), items.map { it.checked })
            assertTrue(items.all { it.enabled })
        }

    private companion object {
        const val SNAP_MM = 100.0
        const val GRID_MM = 500.0
    }
}
