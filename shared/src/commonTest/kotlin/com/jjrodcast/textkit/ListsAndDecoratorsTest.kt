package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Converting paragraphs to/from lists, and interacting with decorators (task checkboxes). */
class ListsAndDecoratorsTest {

    private fun firstDecorator(editor: com.jjrodcast.textkit.editor.core.TextKitEditorManager) =
        editor.getParagraphs().first().children.first().decorator

    @Test
    fun converts_a_paragraph_into_a_bulleted_list() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        assertTrue(
            editor.toListItem(
                TextRange(0, editor.offsetOf("world")),
                from = TextEditorListItem.None,
                to = TextEditorListItem.BulletedList
            )
        )

        assertTrue(firstDecorator(editor) is TextDecoratorModel.BulletDecoratorModel)
        assertTrue(editor.text.contains("Hello world"))
    }

    @Test
    fun converts_a_paragraph_into_a_numbered_list() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.toListItem(TextRange(0, 5), TextEditorListItem.None, TextEditorListItem.NumberedList)

        assertTrue(firstDecorator(editor) is TextDecoratorModel.NumberDecoratorModel)
    }

    @Test
    fun converts_a_paragraph_into_a_task_list() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.toListItem(TextRange(0, 5), TextEditorListItem.None, TextEditorListItem.CheckList)

        assertTrue(firstDecorator(editor) is TextDecoratorModel.TaskDecoratorModel)
    }

    @Test
    fun converts_a_non_first_list_item_back_to_a_paragraph() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)
        // Second item text "two" lives at the tail of the stream.
        val two = editor.rangeOf("two")

        assertTrue(
            editor.toListItem(two, from = TextEditorListItem.NumberedList, to = TextEditorListItem.None)
        )

        // The second paragraph no longer has a decorator; the first list item is untouched.
        val paragraphs = editor.getParagraphs()
        val last = paragraphs.last()
        assertFalse(last.children.any { it.decorator != null })
        assertTrue(paragraphs.first().children.first().decorator is TextDecoratorModel.NumberDecoratorModel)
    }

    @Test
    fun loaded_ordered_list_exposes_number_decorators_on_each_item() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)

        val decorators = editor.getParagraphs().map { it.children.first().decorator }
        assertEquals(2, decorators.size)
        assertTrue(decorators.all { it is TextDecoratorModel.NumberDecoratorModel })
    }

    @Test
    fun toggling_a_task_item_flips_its_checked_state() {
        val editor = editorFrom(SampleDocuments.TASK_LIST)
        val before = firstDecorator(editor) as TextDecoratorModel.TaskDecoratorModel
        assertFalse(before.checked)

        // Offset 2 falls inside the first task decorator marker.
        assertTrue(editor.onDecoratorChange(2))

        val after = firstDecorator(editor) as TextDecoratorModel.TaskDecoratorModel
        assertTrue(after.checked)
    }

    @Test
    fun checkDecorator_is_true_inside_a_decorator_and_false_over_plain_text() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)

        val (insideDecorator, decoratorRange) = editor.checkDecorator(2, 2)
        assertTrue(insideDecorator)
        assertEquals(TextRange(0, 5), decoratorRange)

        val (overText, _) = editor.checkDecorator(6, 7) // inside "one"
        assertFalse(overText)
    }

    @Test
    fun typing_inside_a_numbered_decorator_is_blocked() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)
        val before = editor.text
        // Offset 2 is inside the first "\t\t1. " decorator marker.
        editor.typeText(offset = 2, textToAdd = "X")
        // The decorator is protected: the insertion is a no-op.
        assertEquals(before, editor.text)
    }

    @Test
    fun task_list_loads_checked_and_unchecked_items() {
        val editor = editorFrom(SampleDocuments.TASK_LIST)

        val decorators = editor.getParagraphs()
            .map { it.children.first().decorator }
            .filterIsInstance<TextDecoratorModel.TaskDecoratorModel>()

        assertEquals(2, decorators.size)
        assertFalse(decorators[0].checked)
        assertTrue(decorators[1].checked)
    }
}
