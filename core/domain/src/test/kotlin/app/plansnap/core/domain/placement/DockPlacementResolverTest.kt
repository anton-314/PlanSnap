package app.plansnap.core.domain.placement

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Analytic reference cases of the dock placement (DOMAIN section 10, ADR 0013, ADR 0015). */
class DockPlacementResolverTest {
    private val rect = rectangle(4000, 3000)

    private fun outlines(vararg ids: String) = ids.associate { RoomId(it) to rect }

    private fun resolve(
        rooms: List<app.plansnap.core.model.Room>,
        evaluator: FakeShapeEvaluator,
    ) = runBlocking { DefaultDockPlacementResolver(evaluator).resolve(projectOf(rooms), TOLERANCE) }

    @Test
    fun `given guest docked to top wall of host when resolved then it sits exactly the thickness above`() {
        val outlines = outlines("h", "g")
        val host = rawRoom("h", 4)
        // Host w2 runs from (4000, 3000) to (0, 3000). Offset 0 puts the own end V_1 at (4000, 3000 + 120).
        val guest = rawRoom("g", 4).dockedBy(link("h", 2, "g", 0, 0))

        val project = resolve(listOf(host, guest), FakeShapeEvaluator(outlines)).value()

        val placement = project.room("g").placement
        assertEquals(0.0, placement.originX, EPS_MM)
        assertEquals(3120.0, placement.originY, EPS_MM)
        assertEquals(0.0, placement.rotationRad, EPS_UNIT)
        val world = worldOutline(project.room("g"), rect)
        assertTrue(world[0].isCloseTo(Vec2(0.0, 3120.0), EPS_MM))
        assertTrue(world[2].isCloseTo(Vec2(4000.0, 6120.0), EPS_MM))
        assertDocked(project, outlines, "g", 0.0)
    }

    @Test
    fun `given guest on right wall when resolved then it continues the row with the host thickness gap`() {
        val outlines = outlines("h", "g")
        val host = rawRoom("h", 4, thicknessMm = { 240L })
        // Own w3 runs from (0, 3000) to (0, 0), antiparallel to host w1 without rotation, flush at offset 0.
        val guest = rawRoom("g", 4).dockedBy(link("h", 1, "g", 3, 0))

        val project = resolve(listOf(host, guest), FakeShapeEvaluator(outlines)).value()

        val placement = project.room("g").placement
        assertEquals(4240.0, placement.originX, EPS_MM)
        assertEquals(0.0, placement.originY, EPS_MM)
        assertEquals(0.0, placement.rotationRad, EPS_UNIT)
    }

    @Test
    fun `given different thicknesses when resolved then the host wall thickness separates the rooms`() {
        val outlines = outlines("h", "g")
        val host = rawRoom("h", 4, thicknessMm = { if (it == 1) 365L else 120L })
        val guest = rawRoom("g", 4, thicknessMm = { 50L }).dockedBy(link("h", 1, "g", 0, 0))

        val project = resolve(listOf(host, guest), FakeShapeEvaluator(outlines)).value()

        assertDocked(project, outlines, "g", 0.0)
        // Own w0 points down after docking, so the guest lies right of x = 4000 + 365, from y = 0 to y = 4000.
        val world = worldOutline(project.room("g"), rect)
        assertTrue(world.all { it.x >= 4365.0 - EPS_MM }, "$world")
        assertTrue(world[0].isCloseTo(Vec2(4365.0, 4000.0), EPS_MM), "${world[0]}")
        assertTrue(world[1].isCloseTo(Vec2(4365.0, 0.0), EPS_MM), "${world[1]}")
    }

    @Test
    fun `given rotated and translated host when resolved then docking follows the host frame`() {
        val outlines = outlines("h", "g")
        val host = rawRoom("h", 4, placement = RoomPlacement(1000.0, -2000.0, Math.PI / 2, null))
        val guest = rawRoom("g", 4).dockedBy(link("h", 2, "g", 0, 0))

        val project = resolve(listOf(host, guest), FakeShapeEvaluator(outlines)).value()

        assertDocked(project, outlines, "g", 0.0)
        val placement = project.room("g").placement
        // In the host frame the guest origin is (0, 3120). Rotating by 90° maps it to (-3120, 0).
        assertEquals(1000.0 - 3120.0, placement.originX, EPS_MM)
        assertEquals(-2000.0, placement.originY, EPS_MM)
        assertEquals(Math.PI / 2, placement.rotationRad, EPS_UNIT)
    }

    @Test
    fun `given clockwise host and counter-clockwise guest when resolved then the guest lies outside the host`() {
        val clockwise = reversed(rect)
        val outlines = mapOf(RoomId("h") to clockwise, RoomId("g") to rect)
        val host = rawRoom("h", 4)
        // Host w0 runs up along x = 0. For a clockwise outline its outward side is -x. Different windings make the
        // own w0 run up as well, rotated by 90°, with its start V_0 flush at (-120, 0).
        val guest = rawRoom("g", 4).dockedBy(link("h", 0, "g", 0, 0))

        val project = resolve(listOf(host, guest), FakeShapeEvaluator(outlines)).value()

        assertDocked(project, outlines, "g", 0.0)
        assertEquals(Math.PI / 2, project.room("g").placement.rotationRad, EPS_UNIT)
        val world = worldOutline(project.room("g"), rect)
        assertTrue(world[0].isCloseTo(Vec2(-120.0, 0.0), EPS_MM), "${world[0]}")
        assertTrue(world.all { it.x <= -120.0 + EPS_MM }, "no overlap with the host: $world")
    }

    @Test
    fun `given counter-clockwise host and clockwise guest when resolved then the guest lies outside the host`() {
        val outlines = mapOf(RoomId("h") to rect, RoomId("g") to reversed(rect))
        val host = rawRoom("h", 4, thicknessMm = { 240L })
        // Reversed own w0 runs from (0, 0) to (0, 3000). It is turned parallel to host w1, never antiparallel, because
        // that would put the clockwise guest into the host.
        val guest = rawRoom("g", 4).dockedBy(link("h", 1, "g", 0, 500))

        val project = resolve(listOf(host, guest), FakeShapeEvaluator(outlines)).value()

        assertDocked(project, outlines, "g", 500.0)
        val world = worldOutline(project.room("g"), reversed(rect))
        assertTrue(world[0].isCloseTo(Vec2(4240.0, 500.0), EPS_MM), "${world[0]}")
        assertTrue(world[1].isCloseTo(Vec2(4240.0, 3500.0), EPS_MM), "${world[1]}")
        assertTrue(world.all { it.x >= 4240.0 - EPS_MM }, "no overlap with the host: $world")
        assertEquals(8240.0, world.maxOf { it.x }, EPS_MM)
    }

    @Test
    fun `given two clockwise rooms when resolved then the guest is antiparallel and outside`() {
        val outlines = mapOf(RoomId("h") to reversed(rect), RoomId("g") to reversed(rect))
        val host = rawRoom("h", 4)
        // Reversed w0 of both rooms runs from (0, 0) to (0, 3000), the host outward side is -x.
        val guest = rawRoom("g", 4).dockedBy(link("h", 0, "g", 0, 0))

        val project = resolve(listOf(host, guest), FakeShapeEvaluator(outlines)).value()

        assertDocked(project, outlines, "g", 0.0)
        val world = worldOutline(project.room("g"), reversed(rect))
        assertTrue(world[1].isCloseTo(Vec2(-120.0, 0.0), EPS_MM), "own end V_1 is the reference point: ${world[1]}")
        assertTrue(world.all { it.x <= -120.0 + EPS_MM }, "no overlap with the host: $world")
    }

    @Test
    fun `given a chain when the root moves then every docked room follows`() {
        val outlines = outlines("a", "b", "c")
        val a = rawRoom("a", 4)
        val b = rawRoom("b", 4).dockedBy(link("a", 1, "b", 3, 0))
        val c = rawRoom("c", 4).dockedBy(link("b", 1, "c", 3, 0))
        val evaluator = FakeShapeEvaluator(outlines)

        val first = resolve(listOf(a, b, c), evaluator).value()
        assertEquals(8240.0, first.room("c").placement.originX, EPS_MM)

        val moved = a.copy(placement = RoomPlacement(500.0, 700.0, 0.0, null))
        val second = resolve(listOf(moved, first.room("b"), first.room("c")), evaluator).value()
        assertEquals(8740.0, second.room("c").placement.originX, EPS_MM)
        assertEquals(700.0, second.room("c").placement.originY, EPS_MM)
        assertDocked(second, outlines, "b", 0.0)
        assertDocked(second, outlines, "c", 0.0)
    }

    @Test
    fun `given dependents listed before their host when resolved then topological order is used`() {
        val outlines = outlines("a", "b", "c")
        val c = rawRoom("c", 4).dockedBy(link("b", 1, "c", 3, 0))
        val b = rawRoom("b", 4).dockedBy(link("a", 1, "b", 3, 0))
        val a = rawRoom("a", 4, placement = RoomPlacement(-100.0, 0.0, 0.0, null))

        val project = resolve(listOf(c, b, a), FakeShapeEvaluator(outlines)).value()

        assertEquals(8140.0, project.room("c").placement.originX, EPS_MM)
        assertEquals(listOf("c", "b", "a"), project.rooms.map { it.id.value })
    }

    @Test
    fun `given dock to an opening when the opening moves then the room follows`() {
        val outlines = outlines("h", "g")
        val host = rawRoom("h", 4, openings = listOf(door("d", wallId("h", 0), 1000)))
        val guest = rawRoom("g", 4).dockedBy(link("h", 0, "g", 2, 0).viaOpening("d"))
        val evaluator = FakeShapeEvaluator(outlines)

        val first = resolve(listOf(host, guest), evaluator).value()
        assertDocked(first, outlines, "g", 1000.0)
        val world = worldOutline(first.room("g"), rect)
        assertEquals(1000.0, world.minOf { it.x }, EPS_MM, "the guest starts at the door")
        assertEquals(5000.0, world.maxOf { it.x }, EPS_MM)

        val shifted =
            host.copy(
                openings = kotlinx.collections.immutable.persistentListOf(door("d", wallId("h", 0), 1500)),
            )
        val second = resolve(listOf(shifted, first.room("g")), evaluator).value()
        assertDocked(second, outlines, "g", 1500.0)
        assertEquals(first.room("g").placement.originX + 500.0, second.room("g").placement.originX, EPS_MM)
    }

    @Test
    fun `given guest flush with a door at offset 0 when resolved then its wall covers the door`() {
        val outlines = outlines("h", "g")
        val host = rawRoom("h", 4, openings = listOf(door("d", wallId("h", 0), 0)))
        // Own w2 runs from (4000, 3000) to (0, 3000), antiparallel to host w0, so its end V_3 is the reference point.
        val guest = rawRoom("g", 4).dockedBy(link("h", 0, "g", 2, 0).viaOpening("d"))

        val project = resolve(listOf(host, guest), FakeShapeEvaluator(outlines)).value()

        assertDocked(project, outlines, "g", 0.0)
        val world = worldOutline(project.room("g"), rect)
        assertTrue(world[3].isCloseTo(Vec2(0.0, -120.0), EPS_MM), "${world[3]}")
        assertTrue(world[2].isCloseTo(Vec2(4000.0, -120.0), EPS_MM), "${world[2]}")
        // The door spans x = 0 to 900 on the host wall, the own wall x = 0 to 4000 directly below it.
        assertTrue(world[3].x <= EPS_MM && world[2].x >= 900.0 - EPS_MM, "the door must be covered: $world")
        assertTrue(world.all { it.y <= -120.0 + EPS_MM }, "no overlap with the host: $world")
    }

    @Test
    fun `given clockwise guest flush with a door at offset 0 when resolved then its wall covers the door`() {
        val outlines = mapOf(RoomId("h") to rect, RoomId("g") to reversed(rect))
        val host = rawRoom("h", 4, openings = listOf(door("d", wallId("h", 1), 0)))
        val guest = rawRoom("g", 4).dockedBy(link("h", 1, "g", 1, 0).viaOpening("d"))

        val project = resolve(listOf(host, guest), FakeShapeEvaluator(outlines)).value()

        assertDocked(project, outlines, "g", 0.0)
        val world = worldOutline(project.room("g"), reversed(rect))
        // Different windings: the own start V_1 is the reference point and the own wall runs up with host w1.
        assertTrue(world[1].isCloseTo(Vec2(4120.0, 0.0), EPS_MM), "${world[1]}")
        assertTrue(world[2].isCloseTo(Vec2(4120.0, 4000.0), EPS_MM), "the door must be covered: ${world[2]}")
        assertTrue(world.all { it.x >= 4120.0 - EPS_MM }, "no overlap with the host: $world")
    }

    @Test
    fun `given opening dock when compared to the equivalent wall dock then placements are equal`() {
        val outlines = outlines("h", "g")
        val host = rawRoom("h", 4, openings = listOf(door("d", wallId("h", 0), 1000)))
        val viaOpening = rawRoom("g", 4).dockedBy(link("h", 0, "g", 2, 250).viaOpening("d"))
        val viaWall = rawRoom("g", 4).dockedBy(link("h", 0, "g", 2, 1250))
        val evaluator = FakeShapeEvaluator(outlines)

        val a = resolve(listOf(host, viaOpening), evaluator).value().room("g").placement
        val b = resolve(listOf(host, viaWall), evaluator).value().room("g").placement

        assertEquals(a.copy(dock = null), b.copy(dock = null))
    }

    @Test
    fun `given open host when resolved then the guest keeps its last placement and link`() {
        val outlines = outlines("h", "g")
        val last = RoomPlacement(10.0, 20.0, 0.3, link("h", 1, "g", 3, 0))
        val rooms = listOf(rawRoom("h", 4), rawRoom("g", 4, placement = last))

        val project = resolve(rooms, FakeShapeEvaluator(outlines, open = setOf(RoomId("h")))).value()

        assertEquals(last, project.room("g").placement)
    }

    @Test
    fun `given conflicting own room when resolved then it keeps its last placement and link`() {
        val outlines = outlines("h", "g")
        val last = RoomPlacement(10.0, 20.0, 0.3, link("h", 1, "g", 3, 0))
        val rooms = listOf(rawRoom("h", 4), rawRoom("g", 4, placement = last))
        val input = projectOf(rooms)

        val project =
            runBlocking {
                DefaultDockPlacementResolver(FakeShapeEvaluator(outlines, conflict = setOf(RoomId("g"))))
                    .resolve(input, TOLERANCE)
            }.value()

        assertSame(input, project)
    }

    @Test
    fun `given room docked to a room that kept its placement when resolved then it aligns to the stored placement`() {
        val outlines = outlines("a", "b", "c")
        val kept = RoomPlacement(0.0, 5000.0, 0.0, link("a", 1, "b", 3, 0))
        val rooms =
            listOf(
                rawRoom("a", 4),
                rawRoom("b", 4, placement = kept),
                rawRoom("c", 4).dockedBy(link("b", 1, "c", 3, 0)),
            )

        val project = resolve(rooms, FakeShapeEvaluator(outlines, open = setOf(RoomId("a")))).value()

        assertEquals(kept, project.room("b").placement)
        assertEquals(4120.0, project.room("c").placement.originX, EPS_MM)
        assertEquals(5000.0, project.room("c").placement.originY, EPS_MM)
    }

    @Test
    fun `given adjusted host when resolved then its displayed outline is used`() {
        val outlines = outlines("h", "g")
        val rooms = listOf(rawRoom("h", 4), rawRoom("g", 4).dockedBy(link("h", 1, "g", 3, 0)))

        val project = resolve(rooms, FakeShapeEvaluator(outlines, adjusted = setOf(RoomId("h")))).value()

        assertDocked(project, outlines, "g", 0.0)
    }

    @Test
    fun `given no dock links when resolved then the same project returns without evaluation`() {
        val evaluator = FakeShapeEvaluator(outlines("a", "b"))
        val input = projectOf(listOf(rawRoom("a", 4), rawRoom("b", 4, placement = RoomPlacement(1.0, 2.0, 3.0, null))))

        val project = runBlocking { DefaultDockPlacementResolver(evaluator).resolve(input, TOLERANCE) }.value()

        assertSame(input, project)
        assertEquals(0, evaluator.calls.get())
    }

    @Test
    fun `given a host with several guests when resolved then each room is evaluated once`() {
        val evaluator = FakeShapeEvaluator(outlines("h", "a", "b", "c"))
        val rooms =
            listOf(
                rawRoom("h", 4),
                rawRoom("a", 4).dockedBy(link("h", 0, "a", 2, 0)),
                rawRoom("b", 4).dockedBy(link("h", 1, "b", 3, 0)),
                rawRoom("c", 4).dockedBy(link("h", 2, "c", 0, 0)),
            )

        resolve(rooms, evaluator).value()

        assertEquals(4, evaluator.calls.get())
    }

    @Test
    fun `given failing evaluation when resolved then the failure is returned`() {
        val rooms = listOf(rawRoom("h", 4), rawRoom("g", 4).dockedBy(link("h", 1, "g", 3, 0)))

        val result = resolve(rooms, FakeShapeEvaluator(outlines("h", "g"), failing = setOf(RoomId("g"))))

        assertEquals(
            DomainResult.Failure(DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL)),
            result,
        )
    }

    @Test
    fun `given two rooms docked to each other when resolved then the cycle is rejected`() {
        val rooms =
            listOf(
                rawRoom("a", 4).dockedBy(link("b", 1, "a", 3, 0)),
                rawRoom("b", 4).dockedBy(link("a", 1, "b", 3, 0)),
            )

        val result = resolve(rooms, FakeShapeEvaluator(outlines("a", "b")))

        assertEquals(DomainResult.Failure(DomainError.RuleViolation(DomainRule.DOCK_CYCLE)), result)
    }

    @Test
    fun `given a three room cycle behind a valid chain when resolved then the cycle is rejected`() {
        val rooms =
            listOf(
                rawRoom("r", 4),
                rawRoom("s", 4).dockedBy(link("r", 1, "s", 3, 0)),
                rawRoom("a", 4).dockedBy(link("c", 1, "a", 3, 0)),
                rawRoom("b", 4).dockedBy(link("a", 1, "b", 3, 0)),
                rawRoom("c", 4).dockedBy(link("b", 1, "c", 3, 0)),
                rawRoom("t", 4).dockedBy(link("c", 1, "t", 3, 0)),
            )

        val result = resolve(rooms, FakeShapeEvaluator(outlines("r", "s", "a", "b", "c", "t")))

        assertEquals(DomainResult.Failure(DomainError.RuleViolation(DomainRule.DOCK_CYCLE)), result)
    }

    @Test
    fun `given broken references when resolved then the first violation is returned`() {
        val host = rawRoom("h", 4, openings = listOf(door("d", wallId("h", 0), 0)))
        val cases =
            listOf(
                link("x", 1, "g", 0, 0) to DomainError.NotFound(EntityKind.ROOM, "x"),
                link("h", 9, "g", 0, 0) to DomainError.NotFound(EntityKind.WALL, "h-w9"),
                link("h", 1, "g", 9, 0) to DomainError.NotFound(EntityKind.WALL, "g-w9"),
                link("h", 1, "g", 0, 0).viaOpening("nope") to DomainError.NotFound(EntityKind.OPENING, "nope"),
                link("h", 1, "g", 0, 0).viaOpening("d") to
                    DomainError.RuleViolation(DomainRule.DOCK_OPENING_NOT_ON_WALL),
            )
        for ((dock, error) in cases) {
            val result = resolve(listOf(host, rawRoom("g", 4).dockedBy(dock)), FakeShapeEvaluator(outlines("h", "g")))
            assertEquals(DomainResult.Failure(error), result, "$dock")
        }
    }

    @Test
    fun `given a link to the room itself when checked then it violates DOCK_TO_SELF`() {
        val room = rawRoom("a", 4)
        val project = projectOf(listOf(room))

        assertEquals(
            DomainError.RuleViolation(DomainRule.DOCK_TO_SELF),
            DockTopology.linkError(project, room, link("a", 1, "a", 0, 0)),
        )
    }

    @Test
    fun `given cancelled caller when resolving then cancellation is thrown`() {
        val rooms = listOf(rawRoom("h", 4), rawRoom("g", 4).dockedBy(link("h", 1, "g", 3, 0)))
        val cancelled = Job().apply { cancel() }

        assertThrows<CancellationException> {
            runBlocking {
                withContext(cancelled) {
                    DefaultDockPlacementResolver(FakeShapeEvaluator(outlines("h", "g")))
                        .resolve(projectOf(rooms), TOLERANCE)
                }
            }
        }
    }
}
