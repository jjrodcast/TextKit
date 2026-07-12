package com.jjrodcast.textkit.editor.core.transactions.models

import com.jjrodcast.textkit.editor.core.parser.LinkAttrs
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark

sealed class TextEditorTransactionType {
    data object Format : TextEditorTransactionType() {
        override val marks: Set<Mark> = emptySet()
    }

    data class Link(val href: String) : TextEditorTransactionType() {
        override val marks: Set<Mark>
            get() = if (href.isNotEmpty()) setOf(LinkMark(LinkAttrs(href))) else emptySet()
    }

    data class Color(val color: String?) : TextEditorTransactionType() {
        override val marks: Set<Mark> = emptySet()
    }

    abstract val marks: Set<Mark>
}
