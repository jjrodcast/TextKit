package com.jjrodcast.textkit.editor.core.converters.models

import com.jjrodcast.textkit.editor.core.piecetable.models.RichPiece
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toLevel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.toTextEditorListItem

internal data class PositionalListItem(
    val richPiece: RichPiece,
    val offsetInDocument: Int,
    var newRichPiece: RichPiece = richPiece.copy(),
    val index: Int = 0,
    val positionalListItems: ArrayList<PositionalListItem> = arrayListOf(),
) {
    val type = newRichPiece.decorator.toTextEditorListItem()
    val level: Int = newRichPiece.decorator.toLevel()
    val modified get() = richPiece != newRichPiece
    var isVisited = false

    fun getLevel(defaultLevel: Int = 1) = newRichPiece.decorator.toLevel(defaultLevel)

    fun getOriginalDecoratorLength() = richPiece.decorator?.length ?: 0

    companion object {
        fun PositionalListItem?.getNewDecoratorLength() = this?.newRichPiece?.decorator?.length ?: 0
    }
}
