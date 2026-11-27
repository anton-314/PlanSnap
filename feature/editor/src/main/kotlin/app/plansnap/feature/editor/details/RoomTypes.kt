package app.plansnap.feature.editor.details

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.feature.editor.R

/**
 * One room type template (REQ-MOD-011).
 *
 * @property key the key stored in the room.
 * @property name string resource of the display name.
 */
data class RoomType(
    val key: RoomTypeKey,
    @StringRes val name: Int,
)

/**
 * The catalog of room type templates. The model stores only the key, so the catalog and the display names (German and
 * English) live here, at the display edge.
 */
object RoomTypeCatalog {
    /** The templates in the order the sheet offers them. */
    val types: List<RoomType> =
        listOf(
            RoomType(RoomTypeKey("living"), R.string.room_type_living),
            RoomType(RoomTypeKey("bedroom"), R.string.room_type_bedroom),
            RoomType(RoomTypeKey("children"), R.string.room_type_children),
            RoomType(RoomTypeKey("kitchen"), R.string.room_type_kitchen),
            RoomType(RoomTypeKey("dining"), R.string.room_type_dining),
            RoomType(RoomTypeKey("bathroom"), R.string.room_type_bathroom),
            RoomType(RoomTypeKey("toilet"), R.string.room_type_toilet),
            RoomType(RoomTypeKey("hallway"), R.string.room_type_hallway),
            RoomType(RoomTypeKey("office"), R.string.room_type_office),
            RoomType(RoomTypeKey("storage"), R.string.room_type_storage),
            RoomType(RoomTypeKey("utility"), R.string.room_type_utility),
            RoomType(RoomTypeKey("balcony"), R.string.room_type_balcony),
        )

    /**
     * Display name of [key] in the language of [resources]. A key that is not in the catalog, for example one from an
     * imported project, is shown as it is.
     */
    fun displayName(
        resources: Resources,
        key: RoomTypeKey,
    ): String = types.firstOrNull { it.key == key }?.let { resources.getString(it.name) } ?: key.value
}

/**
 * Display names of room types in the current language, for callers that draw them outside of the sheets, such as the
 * plan canvas (REQ-MOD-011). The function stays the same instance until the resources change, so it can be a key of
 * `remember`.
 */
@Composable
fun rememberRoomTypeName(): (RoomTypeKey) -> String {
    val resources = LocalResources.current
    return remember(resources) { { key -> RoomTypeCatalog.displayName(resources, key) } }
}
