package app.plansnap.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale of docs/UX.md section 6.
 *
 * @property xs 4 dp.
 * @property s 8 dp.
 * @property m 12 dp.
 * @property l 16 dp.
 * @property xl 24 dp.
 */
@Immutable
data class PlanSnapSpacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
)

/**
 * Size tokens of docs/UX.md section 6.
 *
 * @property minTouchTarget Minimum touch target of every interactive element.
 * @property numericKey Minimum edge length of a numeric keypad key.
 * @property icon Standard icon size.
 * @property statusIcon Icon size inside a status badge.
 */
@Immutable
data class PlanSnapSizes(
    val minTouchTarget: Dp = 48.dp,
    val numericKey: Dp = 56.dp,
    val icon: Dp = 24.dp,
    val statusIcon: Dp = 20.dp,
)
