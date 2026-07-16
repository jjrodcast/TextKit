package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.models.TextKitTrigger
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.ui.state.TextKitTokenState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behaviour of the ephemeral `/` slash trigger: it activates a command popup like the other triggers,
 * but committing removes the `/query` WITHOUT inserting a token or leaving a persisted node — the
 * command's own action is what changes the document.
 */
class SlashCommandTest {

    private fun tokenState(doc: String): Pair<TextKitEditorManager, TextKitTokenState> {
        val configuration = createTextKitConfiguration {
            addTrigger { TextKitTrigger.TextKitMentionTrigger() }
            addTrigger { TextKitTrigger.TextKitSlashTrigger() }
        }
        val manager = TextKitEditorManager(configuration).apply { load(doc, isViewer = false) }
        return manager to TextKitTokenState(manager, configuration)
    }

    private fun docWith(text: String) =
        """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"$text"}]}]}"""

    @Test
    fun slash_activates_a_command_trigger_that_is_not_an_atomic_token() {
        val (_, state) = tokenState(docWith("/h1 rest"))
        state.refreshQuery("/h1 rest", TextRange(3)) // caret right after "/h1"
        assertEquals("h1", state.query)
        val trigger = state.activeTrigger
        assertTrue(trigger is TextKitTrigger.TextKitSlashTrigger, "expected slash trigger active")
        assertFalse(trigger.isToken, "slash trigger must be ephemeral (no persisted token)")
    }

    @Test
    fun commit_command_removes_the_slash_query_and_inserts_nothing() {
        val (manager, state) = tokenState(docWith("/h1 rest"))
        val text = manager.text
        val caret = text.indexOf(' ') // caret right after "/h1"
        state.refreshQuery(text, TextRange(caret))

        val newCaret = state.commitCommand(TextRange(caret))
        assertEquals(TextRange(0), newCaret)
        assertTrue(manager.text.startsWith(" rest"), "\"/h1\" not removed: \"${manager.text}\"")
        assertFalse(manager.text.contains("/h1"))
        // Ephemeral: nothing token-like is persisted.
        assertFalse(manager.toJson().contains("\"type\":\"mention\""))
        assertFalse(manager.toJson().contains("\"type\":\"hashtag\""))
        // State resets after committing.
        assertNull(state.query)
        assertNull(state.activeTrigger)
    }

    @Test
    fun commit_command_is_a_noop_for_an_atomic_token_trigger() {
        val (_, state) = tokenState(docWith("@jo rest"))
        state.refreshQuery("@jo rest", TextRange(3)) // mention trigger active
        assertTrue(state.activeTrigger is TextKitTrigger.TextKitMentionTrigger)
        // commitCommand only handles ephemeral triggers; a mention must not be committed this way.
        assertNull(state.commitCommand(TextRange(3)))
    }
}
