package com.plangrid.pgfoundation.texteditor.core.validator

import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.createDecoratorString

internal data class TextInputResult(val model: TextEditorModel)

internal object ListItemValidator {

    private val numberedItem = """^(\d+)\.\s.*$""".toRegex()
    private val bulletedItem = """^\*\s.*$""".toRegex()
    private val taskListUncheckedItem = """^-\[\]\s.*$""".toRegex()
    private val taskListCheckedItem = """^-\[x\]\s.*$""".toRegex()

    internal fun validateInput(input: String): TextInputResult? {
        return getInputResult(input)
    }

    private fun getInputResult(input: String): TextInputResult? {
        numberedItem.find(input)?.let { match ->
            val number = match.groups[1]?.value?.toIntOrNull() ?: 0
            val decorator = TextDecoratorModel.NumberDecoratorModel(number, 1)
            return TextInputResult(TextEditorModel.create(text = decorator.createDecoratorString(), decorator = decorator))
        }

        return when {
            bulletedItem.matches(input) -> {
                val decorator = TextDecoratorModel.BulletDecoratorModel(1)
                TextInputResult(TextEditorModel.create(text = decorator.createDecoratorString(), decorator = decorator))
            }

            taskListUncheckedItem.matches(input) -> {
                val decorator = TextDecoratorModel.TaskDecoratorModel(checked = false, level = 1)
                TextInputResult(TextEditorModel.create(text = decorator.createDecoratorString(), decorator = decorator))
            }

            taskListCheckedItem.matches(input) -> {
                val decorator = TextDecoratorModel.TaskDecoratorModel(checked = true, level = 1)
                TextInputResult(TextEditorModel.create(text = decorator.createDecoratorString(), decorator = decorator))
            }

            else -> null
        }
    }
}
