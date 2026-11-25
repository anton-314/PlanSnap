package app.plansnap.core.drawing.scene

/**
 * Fixed drawing sizes of [DefaultSceneBuilder] in world millimeters. At the export scale 1:50 a label of 150 mm is
 * 3 mm high on paper, at 1:100 it is 1.5 mm.
 *
 * The sizes stay in the scene for every output (ADR 0008, addendum CR-W2-04). Label heights are nominal: the editor
 * renderer draws text at a constant screen size in their ratio, and the export renderer enforces a minimum text
 * height on paper. Distances such as [DIMENSION_OFFSET_MM] are geometry and are never rescaled by a renderer. The
 * only exception is [ROOM_LABEL_LINE_MM]: it is the nominal line spacing of a label block and scales with its text
 * (ADR 0008, addendum CR-W2-05).
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

    /**
     * Nominal distance between the anchors of two consecutive room label lines, 1.5 times [ROOM_LABEL_HEIGHT_MM]. A
     * renderer scales it by the same factor as the text, so the ratio and the gap between the lines stay.
     */
    const val ROOM_LABEL_LINE_MM: Double = 300.0

    /** Height of an interior angle label. */
    const val ANGLE_LABEL_HEIGHT_MM: Double = 120.0

    /** Distance of an interior angle label from its corner, along the inner bisector. */
    const val ANGLE_LABEL_DISTANCE_MM: Double = 300.0
}
