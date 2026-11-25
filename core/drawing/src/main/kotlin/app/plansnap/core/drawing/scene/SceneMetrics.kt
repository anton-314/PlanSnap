package app.plansnap.core.drawing.scene

/**
 * Fixed drawing sizes of [DefaultSceneBuilder] in world millimeters. At the export scale 1:50 a label of 150 mm is
 * 3 mm high on paper, at 1:100 it is 1.5 mm.
 */
internal object SceneMetrics {
    /** Height of a wall or diagonal length label. */
    const val DIMENSION_LABEL_HEIGHT_MM: Double = 150.0

    /** Distance of a dimension line from the outer face of its wall band. */
    const val DIMENSION_OFFSET_MM: Double = 300.0

    /** Gap between the outer face of a wall band and the start of an extension line. */
    const val EXTENSION_GAP_MM: Double = 50.0

    /** Length by which an extension line passes its dimension line. */
    const val EXTENSION_OVERSHOOT_MM: Double = 75.0

    /** Height of a room label line: name, type, area, mark and notes. */
    const val ROOM_LABEL_HEIGHT_MM: Double = 200.0

    /** Distance between the anchors of two consecutive room label lines. */
    const val ROOM_LABEL_LINE_MM: Double = 300.0

    /** Height of an interior angle label. */
    const val ANGLE_LABEL_HEIGHT_MM: Double = 120.0

    /** Distance of an interior angle label from its corner, along the inner bisector. */
    const val ANGLE_LABEL_DISTANCE_MM: Double = 300.0
}
