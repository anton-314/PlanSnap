package app.plansnap.feature.editor.details

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.command.RoomDetails
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.model.AreaFactor
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.feature.editor.shape.SHAPE_ROOM
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorEffect
import kotlinx.collections.immutable.persistentListOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class DetailsHandlerTest {
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

    private val base =
        RoomDetails(
            name = "Küche",
            typeKey = RoomTypeKey("kitchen"),
            notes = "Fenster nach Süden",
            heightOverride = Length(2600),
            areaFactor = AreaFactor(50),
        )

    private fun editing(vararg edits: DetailsIntent) =
        hostOf().also {
            it.handle(DetailsIntent.OpenRoomDetails())
            it.handle(*edits)
            it.handle(DetailsIntent.ApplyRoomDetails)
        }

    @Test
    fun `given a core intent when offered then the handler leaves it to the others`() {
        assertFalse(DetailsHandler().handle(CoreIntent.Undo, hostOf()))
    }

    @Test
    fun `given a selected room when the details open then the draft shows the stored values`() {
        val host = hostOf()
        host.handle(DetailsIntent.OpenRoomDetails())
        val sheet = host.roomSheet
        assertEquals(SHAPE_ROOM, sheet.roomId)
        assertEquals("Küche", sheet.name)
        assertEquals(RoomTypeKey("kitchen"), sheet.typeKey)
        assertEquals("Fenster nach Süden", sheet.notes)
        assertEquals("260.0", sheet.heightText)
        assertEquals("50", sheet.factorText)
        assertTrue(sheet.canApply)
    }

    @Test
    fun `given a room without a height when the details open then the height field is empty`() {
        val host = hostOf(detailedRoom().copy(heightOverride = null))
        host.handle(DetailsIntent.OpenRoomDetails())
        assertEquals("", host.roomSheet.heightText)
    }

    @Test
    fun `given the german locale and meters when the details open then the height uses the comma`() {
        val host = hostOf(settings = AppSettings(lengthUnit = LengthUnit.METER))
        withLocale(Locale.GERMANY) { host.handle(DetailsIntent.OpenRoomDetails()) }
        assertEquals("2,600", host.roomSheet.heightText)
    }

    @Test
    fun `given a room id when the details open then that room opens instead of the selected one`() {
        val host = hostOf()
        host.handle(DetailsIntent.OpenRoomDetails(SHAPE_ROOM))
        assertEquals(SHAPE_ROOM, host.roomSheet.roomId)
    }

    @Test
    fun `given an unknown room when the details open then nothing opens and the user hears about it`() {
        val host = hostOf()
        host.handle(DetailsIntent.OpenRoomDetails(RoomId("gone")))
        assertNull(host.details.sheet)
        assertEquals(
            listOf<EditorEffect>(EditorEffect.Error(DomainError.NotFound(EntityKind.ROOM, "gone"))),
            host.effects,
        )
    }

    @Test
    fun `given no selection when the details open then nothing opens`() {
        val host = hostOf().also { host -> host.update { it.copy(selectedRoomId = null) } }
        host.handle(DetailsIntent.OpenRoomDetails())
        assertNull(host.details.sheet)
        assertTrue(host.effects.isEmpty())
    }

    @Test
    fun `given a new name when applied then one command stores it`() {
        val host = editing(DetailsIntent.NameChanged("  Essküche "))
        assertEquals(
            listOf<ProjectEdit>(ProjectEdit.UpdateRoomDetails(SHAPE_ROOM, base.copy(name = "Essküche"))),
            host.submitted,
        )
        assertNull(host.details.sheet)
    }

    @Test
    fun `given a new type when applied then one command stores it and none removes it`() {
        val host = editing(DetailsIntent.TypeChanged(RoomTypeKey("dining")))
        assertEquals(RoomTypeKey("dining"), (host.submitted.single() as ProjectEdit.UpdateRoomDetails).details.typeKey)
        val removed = editing(DetailsIntent.TypeChanged(null))
        assertNull((removed.submitted.single() as ProjectEdit.UpdateRoomDetails).details.typeKey)
    }

    @Test
    fun `given a new note when applied then one command stores it exactly as typed`() {
        val host = editing(DetailsIntent.NotesChanged("Zeile 1\nZeile 2 "))
        assertEquals("Zeile 1\nZeile 2 ", (host.submitted.single() as ProjectEdit.UpdateRoomDetails).details.notes)
    }

    @Test
    fun `given a height in centimeters when applied then the command carries it in millimeters`() {
        val host = editing(DetailsIntent.HeightChanged("245.5"))
        assertEquals(Length(2455), (host.submitted.single() as ProjectEdit.UpdateRoomDetails).details.heightOverride)
    }

    @Test
    fun `given a comma in the german locale when applied then the height is read with the comma`() {
        val host = hostOf(settings = AppSettings(lengthUnit = LengthUnit.METER))
        withLocale(Locale.GERMANY) {
            host.handle(
                DetailsIntent.OpenRoomDetails(),
                DetailsIntent.HeightChanged("2,45"),
                DetailsIntent.ApplyRoomDetails,
            )
        }
        assertEquals(Length(2450), (host.submitted.single() as ProjectEdit.UpdateRoomDetails).details.heightOverride)
    }

    @Test
    fun `given an empty height when applied then the room returns to the project default height`() {
        val host = editing(DetailsIntent.HeightChanged(""))
        assertNull((host.submitted.single() as ProjectEdit.UpdateRoomDetails).details.heightOverride)
    }

    @Test
    fun `given a factor with a percent sign when applied then the command carries the percent`() {
        val host = editing(DetailsIntent.FactorChanged("75 %"))
        assertEquals(AreaFactor(75), (host.submitted.single() as ProjectEdit.UpdateRoomDetails).details.areaFactor)
    }

    @Test
    fun `given every field changed when applied then they are stored by a single command`() {
        val host =
            editing(
                DetailsIntent.NameChanged("Bad"),
                DetailsIntent.TypeChanged(RoomTypeKey("bathroom")),
                DetailsIntent.NotesChanged("nass"),
                DetailsIntent.HeightChanged("240"),
                DetailsIntent.FactorChanged("0"),
            )
        assertEquals(
            ProjectEdit.UpdateRoomDetails(
                SHAPE_ROOM,
                RoomDetails("Bad", RoomTypeKey("bathroom"), "nass", Length(2400), AreaFactor(0)),
            ),
            host.submitted.single(),
        )
    }

    @Test
    fun `given nothing changed when applied then no command runs and the sheet closes`() {
        val host = editing()
        assertTrue(host.submitted.isEmpty())
        assertNull(host.details.sheet)
    }

    @Test
    fun `given a name that is too long when typed then the name field explains it and apply stays off`() {
        val host = hostOf()
        host.handle(DetailsIntent.OpenRoomDetails(), DetailsIntent.NameChanged("x".repeat(61)))
        assertEquals(
            DomainError.InvalidValue(ValueField.NAME, InvalidValueReason.TOO_LARGE),
            host.roomSheet.errors[DetailsField.NAME],
        )
        assertFalse(host.roomSheet.canApply)
        host.handle(DetailsIntent.ApplyRoomDetails)
        assertTrue(host.submitted.isEmpty())
        assertTrue(host.details.sheet is DetailsSheet.RoomDetails)
    }

    @Test
    fun `given invalid heights when typed then each is rejected at the height field with its reason`() {
        val expected =
            mapOf(
                "abc" to InvalidValueReason.MALFORMED,
                "0" to InvalidValueReason.TOO_SMALL,
                "-3" to InvalidValueReason.TOO_SMALL,
                "245.55" to InvalidValueReason.NOT_REPRESENTABLE,
            )
        expected.forEach { (text, reason) ->
            val host = hostOf()
            host.handle(
                DetailsIntent.OpenRoomDetails(),
                DetailsIntent.HeightChanged(text),
                DetailsIntent.ApplyRoomDetails,
            )
            assertEquals(
                text,
                DomainError.InvalidValue(ValueField.ROOM_HEIGHT, reason),
                host.roomSheet.errors[DetailsField.HEIGHT],
            )
            assertTrue(text, host.submitted.isEmpty())
        }
    }

    @Test
    fun `given invalid factors when typed then each is rejected at the factor field with its reason`() {
        val expected =
            mapOf(
                "101" to InvalidValueReason.TOO_LARGE,
                "99999999999999" to InvalidValueReason.TOO_LARGE,
                "-1" to InvalidValueReason.TOO_SMALL,
                "50,5" to InvalidValueReason.NOT_REPRESENTABLE,
                "fifty" to InvalidValueReason.MALFORMED,
            )
        expected.forEach { (text, reason) ->
            val host = hostOf()
            host.handle(
                DetailsIntent.OpenRoomDetails(),
                DetailsIntent.FactorChanged(text),
                DetailsIntent.ApplyRoomDetails,
            )
            assertEquals(
                text,
                DomainError.InvalidValue(ValueField.AREA_FACTOR, reason),
                host.roomSheet.errors[DetailsField.FACTOR],
            )
            assertTrue(text, host.submitted.isEmpty())
        }
    }

    @Test
    fun `given an empty factor when applied then it is rejected but typing it shows no error`() {
        val host = hostOf()
        host.handle(DetailsIntent.OpenRoomDetails(), DetailsIntent.FactorChanged(""))
        assertTrue(host.roomSheet.errors.isEmpty())
        assertFalse(host.roomSheet.canApply)
        host.handle(DetailsIntent.ApplyRoomDetails)
        assertEquals(
            DomainError.InvalidValue(ValueField.AREA_FACTOR, InvalidValueReason.MALFORMED),
            host.roomSheet.errors[DetailsField.FACTOR],
        )
        assertTrue(host.submitted.isEmpty())
    }

    @Test
    fun `given an error when the value is corrected then the error disappears`() {
        val host = hostOf()
        host.handle(
            DetailsIntent.OpenRoomDetails(),
            DetailsIntent.FactorChanged("101"),
            DetailsIntent.FactorChanged("100"),
        )
        assertTrue(host.roomSheet.errors.isEmpty())
        assertTrue(host.roomSheet.canApply)
    }

    @Test
    fun `given a room that vanished when applied then the user hears about it and the sheet closes`() {
        val host = hostOf()
        host.handle(DetailsIntent.OpenRoomDetails(), DetailsIntent.NameChanged("Neu"))
        host.update { it.copy(project = it.project!!.copy(rooms = persistentListOf())) }
        host.handle(DetailsIntent.ApplyRoomDetails)
        assertTrue(host.submitted.isEmpty())
        assertNull(host.details.sheet)
        assertEquals(
            listOf<EditorEffect>(EditorEffect.Error(DomainError.NotFound(EntityKind.ROOM, SHAPE_ROOM.value))),
            host.effects,
        )
    }

    @Test
    fun `given an open sheet when dismissed then the draft is dropped without a command`() {
        val host = hostOf()
        host.handle(DetailsIntent.OpenRoomDetails(), DetailsIntent.NameChanged("Neu"), DetailsIntent.Dismiss)
        assertNull(host.details.sheet)
        assertTrue(host.submitted.isEmpty())
    }

    @Test
    fun `given an edit without an open sheet when offered then it is consumed and changes nothing`() {
        val host = hostOf()
        host.handle(DetailsIntent.NameChanged("x"), DetailsIntent.ApplyRoomDetails, DetailsIntent.ApplyProjectHeight)
        assertNull(host.details.sheet)
        assertTrue(host.submitted.isEmpty())
    }

    @Test
    fun `given the overview when opened then the project height is prefilled in the display unit`() {
        val host = hostOf()
        host.handle(DetailsIntent.OpenOverview)
        assertEquals("250.0", host.overviewSheet.projectHeightText)
    }

    @Test
    fun `given a new project height when applied then one command stores it`() {
        val host = hostOf()
        host.handle(
            DetailsIntent.OpenOverview,
            DetailsIntent.ProjectHeightChanged("260"),
            DetailsIntent.ApplyProjectHeight,
        )
        assertEquals(listOf<ProjectEdit>(ProjectEdit.SetProjectHeight(Length(2600))), host.submitted)
    }

    @Test
    fun `given the unchanged project height when applied then no command runs`() {
        val host = hostOf()
        host.handle(DetailsIntent.OpenOverview, DetailsIntent.ApplyProjectHeight)
        assertTrue(host.submitted.isEmpty())
    }

    @Test
    fun `given invalid project heights when typed then each is rejected at the field`() {
        listOf("abc", "0", "").forEach { text ->
            val host = hostOf()
            host.handle(
                DetailsIntent.OpenOverview,
                DetailsIntent.ProjectHeightChanged(text),
                DetailsIntent.ApplyProjectHeight,
            )
            assertTrue(text, host.overviewSheet.errors.containsKey(DetailsField.PROJECT_HEIGHT))
            assertTrue(text, host.submitted.isEmpty())
        }
    }

    @Test
    fun `given the intent list when the menu is read then it offers the details and the overview`() {
        assertEquals(
            listOf<DetailsIntent>(DetailsIntent.OpenRoomDetails(), DetailsIntent.OpenOverview),
            DetailsMenu.entries.map { it.intent },
        )
        assertEquals(
            2,
            DetailsMenu.entries
                .map { it.testTag }
                .toSet()
                .size,
        )
    }
}
