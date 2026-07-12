package com.jjrodcast.textkit.editor.core.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@SerialName(ListTypes.TaskList)
internal data class TaskList(val content: List<TaskListItem> = emptyList()) : BaseParagraph() {
    override val type: String = ListTypes.TaskList
}

@Serializable
@SerialName(ListTypes.OrderedList)
internal data class OrderedList(
    val attrs: ListAttrs = ListAttrs(),
    val content: List<BaseText> = emptyList()
) : BaseParagraph() {
    override val type: String = ListTypes.OrderedList
}

@Serializable
@SerialName(ListItemTypes.TaskListItem)
internal data class TaskListItem(
    val attrs: TaskListAttrs = TaskListAttrs(),
    val content: List<BaseParagraph> = emptyList()
) : BaseText() {
    @Transient
    override val key: String = ListItemTypes.TaskListItem
}

@Serializable
@SerialName(ListItemTypes.DefaultListItem)
internal data class ListItem(val content: List<BaseParagraph> = emptyList()) : BaseText() {
    @Transient
    override val key: String = ListItemTypes.DefaultListItem
}

@Serializable
@SerialName(ListTypes.BulletList)
internal data class BulletedList(val content: List<BaseText> = emptyList()) : BaseParagraph() {
    override val type: String = ListTypes.BulletList
}

internal object ListTypes {
    const val TaskList = "taskList"
    const val OrderedList = "orderedList"
    const val BulletList = "bulletList"
}

internal object ListItemTypes {
    const val TaskListItem = "taskItem"
    const val DefaultListItem = "listItem"
}
