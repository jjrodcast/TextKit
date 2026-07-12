package com.jjrodcast.textkit.editor.core.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(BlockquoteType.Blockquote)
internal data class Blockquote(val content: List<BaseParagraph> = emptyList()) : BaseParagraph() {
    override val type: String = BlockquoteType.Blockquote
}

internal object BlockquoteType {
    const val Blockquote = "blockquote"
}

/*
{
  "type": "blockquote",
  "content": [
    {
      "type": "paragraph",
      "content": [
        {
          "type": "text",
          "text": "Texto "
        },
        {
          "type": "text",
          "text": "importante",
          "marks": [
            {
              "type": "bold"
            }
          ]
        }
      ]
    }
  ]
}
 */