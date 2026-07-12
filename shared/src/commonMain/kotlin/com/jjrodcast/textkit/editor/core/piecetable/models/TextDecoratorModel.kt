package com.jjrodcast.textkit.editor.core.piecetable.models

import com.jjrodcast.textkit.editor.components.TextEditorDecorator
import com.jjrodcast.textkit.editor.components.TextEditorDecoratorItem
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.utils.BULLET_DECORATOR_LEVEL_ONE
import com.jjrodcast.textkit.editor.utils.BULLET_DECORATOR_LEVEL_THREE
import com.jjrodcast.textkit.editor.utils.BULLET_DECORATOR_LEVEL_TWO
import com.jjrodcast.textkit.editor.utils.DOT
import com.jjrodcast.textkit.editor.utils.EMPTY
import com.jjrodcast.textkit.editor.utils.SPACE
import com.jjrodcast.textkit.editor.utils.TABS
import com.jjrodcast.textkit.editor.utils.TASK_DECORATOR_INTERACTIVE
import com.jjrodcast.textkit.editor.utils.TASK_DECORATOR_UNCHECKED_INTERACTIVE
import kotlinx.serialization.Serializable

@Serializable
sealed class TextDecoratorModel {
    abstract val level: Int

    abstract val key: String
    abstract fun copyValue(level: Int, nestedCount: Int = 0): TextDecoratorModel
    abstract fun toCount(): Int
    abstract fun toNestedCount(): Int

    val length: Int get() {
        return getDecoratorString().length
    }

    private fun getDecoratorString() = when (this) {
        is NumberDecoratorModel -> "$count$DOT$SPACE"
        is BulletDecoratorModel -> {
            when (level) {
                1 -> BULLET_DECORATOR_LEVEL_ONE
                2 -> BULLET_DECORATOR_LEVEL_TWO
                else -> BULLET_DECORATOR_LEVEL_THREE
            }
        }

        is TaskDecoratorModel -> {
            when (checked) {
                true -> TASK_DECORATOR_INTERACTIVE
                else -> TASK_DECORATOR_UNCHECKED_INTERACTIVE
            }
        }

        is BlockquoteDecorator -> EMPTY
    }

    @Serializable
    data class NumberDecoratorModel(val count: Int, override val level: Int = 0) : TextDecoratorModel() {
        override val key = NUMBERED_LIST_KEY
        override fun copyValue(level: Int, nestedCount: Int) = copy(count = count, level = level)
        override fun toCount() = count
        override fun toNestedCount() = 0

        companion object {
            internal const val NUMBERED_LIST_KEY = "NDM"
        }
    }

    @Serializable
    data class BulletDecoratorModel(override val level: Int = 0) : TextDecoratorModel() {
        override val key = BULLETED_LIST_KEY
        override fun copyValue(level: Int, nestedCount: Int) = this.copy(level = level)
        override fun toCount() = 0
        override fun toNestedCount() = 0

        companion object {
            internal const val BULLETED_LIST_KEY = "BDM"
        }
    }

    @Serializable
    data class TaskDecoratorModel(
        val checked: Boolean = false,
        val nestedCount: Int = 0,
        override val level: Int = 0
    ) : TextDecoratorModel() {

        override val key = TASK_LIST_KEY
        override fun copyValue(level: Int, nestedCount: Int) = copy(level = level, nestedCount = nestedCount)
        override fun toCount() = 0
        override fun toNestedCount() = 0

        companion object {
            internal const val TASK_LIST_KEY = "TDM"
        }
    }

    @Serializable
    data class BlockquoteDecorator(val group: Int) : TextDecoratorModel() {
        override val level = -1
        override val key = BLOCK_QUOTE_KEY
        override fun copyValue(level: Int, nestedCount: Int) = this
        override fun toCount() = 0
        override fun toNestedCount() = 0

        companion object {
            internal const val BLOCK_QUOTE_KEY = "BQ"
        }
    }

    companion object {

        internal const val NONE_KEY = "NONE"
        private const val DEFAULT_LEVELS = 45

        private val tabsCache: Array<String> by lazy {
            Array(DEFAULT_LEVELS) { level -> if (level <= 1) TABS else TABS.repeat(level) }
        }

        fun TextDecoratorModel?.createDecoratorString(): String {
            if (this == null) return ""
            val decoratorString = getDecoratorString()
            return when (level) {
                -1 -> decoratorString
                else -> {
                    val tabs = if (level < tabsCache.size) tabsCache[level] else TABS.repeat(level)
                    "$tabs$decoratorString"
                }
            }
        }

        internal fun TextDecoratorModel?.toTextEditorListItem(): TextEditorDecoratorItem {
            return when (this) {
                is NumberDecoratorModel -> TextEditorListItem.NumberedList
                is BulletDecoratorModel -> TextEditorListItem.BulletedList
                is TaskDecoratorModel -> TextEditorListItem.CheckList
                is BlockquoteDecorator -> TextEditorDecorator.Blockquote
                else -> TextEditorListItem.None
            }
        }

        internal fun TextDecoratorModel?.toLevel(defaultLevel: Int = 1) = when (this) {
            null -> defaultLevel
            else -> level
        }

        internal fun TextDecoratorModel?.toNewDecoratorModel(count: Int, coerceLevel: Boolean = false): TextDecoratorModel? = when (this) {
            is NumberDecoratorModel -> NumberDecoratorModel(count = count, level = level)
            is BulletDecoratorModel -> BulletDecoratorModel(level = if (coerceLevel) level.coerceAtLeast(1) else level)
            is TaskDecoratorModel -> TaskDecoratorModel(level = if (coerceLevel) level.coerceAtLeast(1) else level, checked = checked)
            else -> null
        }
    }
}
