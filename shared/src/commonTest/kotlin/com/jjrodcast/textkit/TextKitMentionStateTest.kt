package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.models.TextKitTrigger
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.ui.state.TextKitMentionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Behaviour of the mention popup's activation logic ([TextKitMentionState]), driven purely by the
 * caret position so the popup re-opens when the caret is moved back next to an existing `@`.
 */
class TextKitMentionStateTest {

    private fun mentionState(): TextKitMentionState {
        val configuration = createTextKitConfiguration {
            addTrigger { TextKitTrigger.TextKitMentionTrigger() }
        }
        // Empty document: there are no committed mentions, so activation depends only on the text /
        // caret passed to refreshQuery below.
        val manager = TextKitEditorManager(configuration).apply { load("{}", isViewer = false) }
        return TextKitMentionState(manager, configuration)
    }

    @Test
    fun repositioning_caret_next_to_empty_trigger_reactivates_popup() {
        val mention = mentionState()
        val text = "@ hello"

        // Just typed "@": caret right after it → popup active with an empty query.
        mention.refreshQuery(text, TextRange(1))
        assertEquals("", mention.query)

        // Caret moved away → popup closes.
        mention.refreshQuery(text, TextRange(5))
        assertNull(mention.query)

        // Caret moved back next to the "@" → popup re-opens (this is the reported bug).
        mention.refreshQuery(text, TextRange(1))
        assertEquals("", mention.query)
    }

    @Test
    fun query_tracks_text_typed_after_the_trigger() {
        val mention = mentionState()
        mention.refreshQuery("@jor rest", TextRange(4)) // caret right after "@jor"
        assertEquals("jor", mention.query)
    }

    @Test
    fun whitespace_between_trigger_and_caret_closes_popup() {
        val mention = mentionState()
        mention.refreshQuery("@ x", TextRange(3)) // span "@ x" contains a space
        assertNull(mention.query)
    }

    @Test
    fun caret_before_the_trigger_does_not_activate() {
        val mention = mentionState()
        mention.refreshQuery("a @", TextRange(0)) // caret at the very start, "@" is to the right
        assertNull(mention.query)
    }

    @Test
    fun email_like_at_is_not_treated_as_a_mention() {
        val mention = mentionState()
        mention.refreshQuery("mail@host", TextRange(9)) // "@" is not at a word boundary
        assertNull(mention.query)
    }

    @Test
    fun dismiss_keeps_popup_closed_until_caret_leaves_the_trigger() {
        val mention = mentionState()
        val text = "@ hello"

        mention.refreshQuery(text, TextRange(1))
        assertEquals("", mention.query)

        // User dismisses (e.g. Escape): stays closed even though the caret is still by the "@".
        mention.dismiss()
        assertNull(mention.query)
        mention.refreshQuery(text, TextRange(1))
        assertNull(mention.query)

        // Caret leaves the trigger, then returns → suppression is cleared and it re-opens.
        mention.refreshQuery(text, TextRange(5))
        mention.refreshQuery(text, TextRange(1))
        assertEquals("", mention.query)
    }
}
