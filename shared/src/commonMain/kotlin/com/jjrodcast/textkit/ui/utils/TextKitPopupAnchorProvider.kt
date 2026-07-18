package com.jjrodcast.textkit.ui.utils

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import com.jjrodcast.textkit.ui.TextKitFormattingBar
import kotlin.math.roundToInt

object TextKitPopupAnchorProvider {

    /**
     * A [PopupPositionProvider] that anchors a `Popup` to [anchorBoundsInWindow] (as delivered by
     * [TextKitFormattingBar]'s `onTextAndColorClick`). It places the popup [gap] px below the anchor,
     * flips it above when it would overflow the bottom, and clamps it horizontally so it always stays
     * inside the window — i.e. it adapts to the screen.
     *
     * ```kotlin
     * var anchor by remember { mutableStateOf<Rect?>(null) }
     * TextKitFormattingBar(/* … */ onTextAndColorClick = { anchor = it })
     * anchor?.let {
     *     Popup(
     *         popupPositionProvider = TextKitPopupAnchorProvider.positionProvider(it),
     *         onDismissRequest = { anchor = null },
     *     ) { /* your color & size picker */ }
     * }
     * ```
     */
    fun positionProvider(
        anchorBoundsInWindow: Rect,
        gap: Int = 8,
    ): PopupPositionProvider = object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowSize: IntSize,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
        ): IntOffset {
            // Align the popup's left edge with the anchor, clamped so it never spills past the window.
            val x = anchorBoundsInWindow.left.roundToInt()
                .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
            // Prefer below the anchor; flip above when it would overflow the bottom (unless above is worse).
            val below = anchorBoundsInWindow.bottom.roundToInt() + gap
            val above = anchorBoundsInWindow.top.roundToInt() - gap - popupContentSize.height
            val y =
                if (below + popupContentSize.height <= windowSize.height || above < 0) below else above
            return IntOffset(x, y)
        }
    }

}