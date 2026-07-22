package com.jjrodcast.textkit.editor.core.export

import com.jjrodcast.textkit.editor.core.parser.TextEditorDocument

/**
 * Turns a parsed [TextEditorDocument] into a text representation.
 *
 * The document is already an AST — `TextEditorDocument` → `BaseParagraph` → `BaseText` are sealed
 * hierarchies — so an implementation is a visitor over that tree: one exhaustive `when` per node
 * level, no piece-table knowledge required.
 *
 * Keeping the export layer behind this interface means the output format is a strategy: [HtmlSerializer]
 * is the first implementation, and further formats can be added without touching call sites.
 */
internal interface DocumentSerializer {

    /** Serializes [document]; returns an empty string for an empty document. */
    fun serialize(document: TextEditorDocument): String
}
