package com.jjrodcast.textkit.editor.core.piecetable.models

internal abstract class Piece {
    abstract val source: Source
    abstract val offset: Int
    abstract val length: Int
    abstract val decorator: TextDecoratorModel?
}
