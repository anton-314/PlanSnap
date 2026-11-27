package app.plansnap.feature.editor.config

/**
 * The window shapes the editor has to survive (REQ-UX-009): a phone in both orientations, a tablet in both
 * orientations, a foldable folded and unfolded, and half of a phone screen in multi window mode. All use mdpi, so one
 * pixel is one dp.
 */
internal enum class DeviceConfig(
    val widthDp: Int,
    val heightDp: Int,
    val qualifiers: String,
) {
    PHONE_PORTRAIT(360, 740, "en-rUS-w360dp-h740dp-normal-long-port-notnight-mdpi"),
    PHONE_LANDSCAPE(740, 360, "en-rUS-w740dp-h360dp-normal-long-land-notnight-mdpi"),
    TABLET_PORTRAIT(800, 1280, "en-rUS-sw800dp-w800dp-h1280dp-xlarge-notlong-port-notnight-mdpi"),
    TABLET_LANDSCAPE(1280, 800, "en-rUS-sw800dp-w1280dp-h800dp-xlarge-notlong-land-notnight-mdpi"),
    FOLDABLE_CLOSED(280, 653, "en-rUS-sw280dp-w280dp-h653dp-normal-long-port-notnight-mdpi"),
    FOLDABLE_OPEN(673, 841, "en-rUS-sw673dp-w673dp-h841dp-large-notlong-port-notnight-mdpi"),
    SPLIT_SCREEN(360, 360, "en-rUS-sw360dp-w360dp-h360dp-normal-notlong-port-notnight-mdpi"),
    ;

    companion object {
        /** Every change of the window a test walks through: from portrait through all shapes and back. */
        val TOUR: List<DeviceConfig> = entries.drop(1) + PHONE_PORTRAIT
    }
}
