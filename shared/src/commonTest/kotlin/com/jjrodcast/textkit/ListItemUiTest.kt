package com.jjrodcast.textkit

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
import com.jjrodcast.textkit.editor.core.parser.TextAlign
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.ui.listlayout.ViewerBlock
import com.jjrodcast.textkit.ui.listlayout.editorSegmentsNeedOffsetMapping
import com.jjrodcast.textkit.ui.listlayout.normalizeTrailingParagraphBreakCaret
import com.jjrodcast.textkit.ui.listlayout.resolveParagraphCaretFieldOffset
import com.jjrodcast.textkit.ui.listlayout.resolveParagraphOperationOffset
import com.jjrodcast.textkit.ui.listlayout.usesSplitListLayout
import com.jjrodcast.textkit.ui.listlayout.viewerIndentLabel
import com.jjrodcast.textkit.ui.listlayout.viewerMarkerLabel
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListItemUiTest {

    private fun stateWith(json: String): TextKitState =
        TextKitState(json, createTextKitConfiguration()).apply { setup() }

    /** Simulates the user typing [text] at [fieldOffset] (field coordinates), as BasicTextField would. */
    private fun TextKitState.simulateTypingAt(fieldOffset: Int, text: String) {
        val before = textFieldValue
        val inserted = before.text.substring(0, fieldOffset) + text +
            before.text.substring(fieldOffset)
        onTextFieldChange(
            TextFieldValue(
                text = inserted,
                selection = TextRange(fieldOffset + text.length),
            )
        )
    }

    private fun centeredBulletListDoc() = """
        {"type":"doc","content":[
          {"type":"bulletList","content":[
            {"type":"listItem","content":[
              {"type":"paragraph","attrs":{"textAlign":"center"},"content":[
                {"type":"text","text":"centered item"}
              ]}
            ]}
          ]}
        ]}
    """

    @Test
    fun splitLayoutDetectedForAlignedListItem() {
        val state = stateWith(centeredBulletListDoc())
        assertTrue(state.paragraphs.first().usesSplitListLayout())
        assertTrue(editorSegmentsNeedOffsetMapping(state.editorSegments()))
    }

    @Test
    fun leftAlignedListUsesSplitLayout() {
        val state = stateWith(SampleDocuments.ORDERED_LIST)
        assertTrue(state.paragraphs.first().usesSplitListLayout())
        assertTrue(editorSegmentsNeedOffsetMapping(state.editorSegments()))
    }

    @Test
    fun viewerListItemBlockOmitsDecoratorFromContent() {
        val state = stateWith(centeredBulletListDoc())
        val block = state.viewerBlocks.single() as ViewerBlock.ListItem
        assertFalse(block.content.text.contains('•'))
        assertTrue(block.content.text.contains("centered item"))
        assertEquals(TextAlign.Center, block.textAlign)
    }

    @Test
    fun editorDisplayOmitsDecoratorForAlignedList() {
        val state = stateWith(centeredBulletListDoc())
        val fieldText = state.textFieldValue.text
        val transformed = state.visualTransformation.filter(AnnotatedString(fieldText))
        assertTrue(transformed.text.length < fieldText.length)
        assertFalse(transformed.text.text.contains('•'))
    }

    @Test
    fun editorOffsetMappingAtContentStart() {
        val state = stateWith(centeredBulletListDoc())
        val fieldText = state.textFieldValue.text
        val transformed = state.visualTransformation.filter(AnnotatedString(fieldText))
        val contentStart = fieldText.indexOf('c')
        // Display position 0 is the marker's kept trailing space (visually zero-width, #135); the
        // content begins one past it. Both display 0 and 1 resolve back to the field content start.
        assertEquals(1, transformed.offsetMapping.originalToTransformed(contentStart))
        assertEquals(contentStart, transformed.offsetMapping.transformedToOriginal(0))
        assertEquals(contentStart, transformed.offsetMapping.transformedToOriginal(1))
    }

    @Test
    fun viewerListItemContentIsCenterAligned() {
        val state = stateWith(centeredBulletListDoc())
        val block = state.viewerBlocks.single() as ViewerBlock.ListItem
        assertEquals(TextAlign.Center, block.textAlign)
        // Alignment is applied on the content BasicText; ParagraphStyle stays left in split layout.
        val aligns = block.content.paragraphStyles.map { it.item.textAlign }
        assertTrue(aligns.all { it == ComposeTextAlign.Left })
    }

    @Test
    fun viewerGutterSplitsIndentFromMarker() {
        val state = stateWith(SampleDocuments.ORDERED_LIST)
        val block = state.viewerBlocks.first() as ViewerBlock.ListItem
        assertTrue(block.gutter.viewerIndentLabel().contains('\t'))
        assertFalse(block.gutter.viewerMarkerLabel().contains('\t'))
        assertTrue(block.gutter.viewerMarkerLabel().contains('1'))
    }

    @Test
    fun viewerOrderedListUsesSharedMarkerColumnWidth() {
        val doc = """
            {"type":"doc","content":[
              {"type":"orderedList","attrs":{"start":1},"content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"one"}]}]},
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"ten"}]}]}
              ]}
            ]}
        """
        val state = stateWith(doc)
        val gutters = state.viewerBlocks.filterIsInstance<ViewerBlock.ListItem>().map { it.gutter }
        assertEquals("1. ", gutters[0].viewerMarkerLabel())
        assertEquals("2. ", gutters[1].viewerMarkerLabel())
    }

    @Test
    fun applyAlignAtSecondListItemContentStartTargetsThatItem() {
        val doc = """
            {"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"Before"}]},
              {"type":"orderedList","attrs":{"start":1},"content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"one"}]}]},
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"two"}]}]}
              ]}
            ]}
        """
        val state = stateWith(doc)
        val text = state.textFieldValue.text
        val twoIndex = text.indexOf("two")
        val listItemTwoStart = text.indexOf('\t', text.indexOf("two") - 1).let { tab ->
            // field start of second list item (decorator)
            text.lastIndexOf('\n', twoIndex) + 1
        }
        val contentStart = twoIndex

        // Caret at content start (correct field offset)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(contentStart)))
        state.applyTextAlignment(TextAlign.Center)
        assertEquals(TextAlign.Left, state.paragraphs[0].textAlign, "plain paragraph unchanged")
        assertEquals(TextAlign.Left, state.paragraphs[1].textAlign, "first list item unchanged")
        assertEquals(TextAlign.Center, state.paragraphs[2].textAlign, "second list item centered")

        // Caret on `\n` at end of first list item (display still on item 1's line) → item 1.
        val state2 = stateWith(doc)
        val beforeSecondItem = listItemTwoStart - 1
        assertEquals('\n', text[beforeSecondItem])
        val mapping = state.visualTransformation.filter(AnnotatedString(text)).offsetMapping
        val itemTwoSegment = state2.editorSegments().last { it.gutter != null }
        assertTrue(mapping.originalToTransformed(beforeSecondItem) < itemTwoSegment.displayStart)
        state2.onTextFieldChange(state2.textFieldValue.copy(selection = TextRange(beforeSecondItem)))
        state2.applyTextAlignment(TextAlign.Right)
        assertEquals(TextAlign.Left, state2.paragraphs[0].textAlign)
        assertEquals(TextAlign.Right, state2.paragraphs[1].textAlign)
        assertEquals(TextAlign.Left, state2.paragraphs[2].textAlign)

        // Same field `\n`, but display caret already on item 2's line → item 2.
        val resolvedStart = resolveParagraphOperationOffset(
            fieldOffset = beforeSecondItem,
            displayOffset = itemTwoSegment.displayStart,
            segments = state2.editorSegments(),
            textLength = text.length,
            isAtEndOfParagraph = { false },
            paragraphStart = { 0 },
        )
        assertEquals(twoIndex, resolvedStart)
    }

    @Test
    fun applyAlignAtFirstListItemAfterPlainTargetsListItem() {
        val doc = """
            {"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"Before"}]},
              {"type":"bulletList","content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"item"}]}]}
              ]}
            ]}
        """
        val state = stateWith(doc)
        val text = state.textFieldValue.text
        val itemIndex = text.indexOf("item")
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(itemIndex)))
        state.applyTextAlignment(TextAlign.Center)
        assertEquals(TextAlign.Left, state.paragraphs[0].textAlign)
        assertEquals(TextAlign.Center, state.paragraphs[1].textAlign)
    }

    @Test
    fun applyAlignAtListItemEndTargetsThatItemNotNext() {
        val state = stateWith(SampleDocuments.ORDERED_LIST)
        val text = state.textFieldValue.text
        val firstItemBreak = text.indexOf('\n')
        assertTrue(firstItemBreak > 0)

        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(firstItemBreak)))
        state.applyTextAlignment(TextAlign.Center)

        assertEquals(TextAlign.Center, state.paragraphs[0].textAlign)
        if (state.paragraphs.size > 1) {
            assertEquals(TextAlign.Left, state.paragraphs[1].textAlign)
        }
    }

    @Test
    fun applyAlignAtPlainParagraphEndTargetsThatParagraph() {
        val state = stateWith(SampleDocuments.TWO_PARAGRAPHS)
        val text = state.textFieldValue.text
        val firstParagraphEnd = text.indexOf('\n')
        assertTrue(firstParagraphEnd > 0)

        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(firstParagraphEnd)))
        state.applyTextAlignment(TextAlign.Center)

        assertEquals(TextAlign.Center, state.paragraphs[0].textAlign)
        assertEquals(TextAlign.Left, state.paragraphs[1].textAlign)
    }

    @Test
    fun applyAlignAtPlainParagraphBeforeListTargetsPlainParagraph() {
        val doc = """
            {"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"Before"}]},
              {"type":"bulletList","content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"item"}]}]}
              ]}
            ]}
        """
        val state = stateWith(doc)
        val text = state.textFieldValue.text
        val beforeListBreak = text.indexOf('\n')
        assertEquals("Before", text.substring(0, beforeListBreak))

        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(beforeListBreak)))
        state.applyTextAlignment(TextAlign.Right)

        assertEquals(TextAlign.Right, state.paragraphs[0].textAlign)
        assertEquals(TextAlign.Left, state.paragraphs[1].textAlign)
    }

    @Test
    fun alignChangeKeepsSplitLayoutStable() {
        val state = stateWith(SampleDocuments.ORDERED_LIST)
        assertTrue(state.paragraphs.first().usesSplitListLayout())
        state.applyTextAlignment(TextAlign.Center)
        assertTrue(state.paragraphs.first().usesSplitListLayout())
        state.applyTextAlignment(TextAlign.Right)
        assertTrue(state.paragraphs.first().usesSplitListLayout())
    }

    @Test
    fun normalizeTrailingBreakCaretMovesIntoParagraphContent() {
        assertEquals(4, normalizeTrailingParagraphBreakCaret(
            offset = 5,
            isEndOfParagraph = true,
            paragraphStart = 0,
        ))
        assertEquals(5, normalizeTrailingParagraphBreakCaret(
            offset = 5,
            isEndOfParagraph = false,
            paragraphStart = 0,
        ))
    }

    @Test
    fun resolveCaretOnLineBreakBeforePlainParagraphStaysOnBreak() {
        val state = stateWith("""
            {"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"A"}]},
              {"type":"bulletList","content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"B"}]}]}
              ]}
            ]}
        """)
        val segs = state.editorSegments()
        val listSegment = segs.first { it.gutter != null }
        val resolved = resolveParagraphCaretFieldOffset(
            rawOffset = listSegment.fieldStart - 1,
            segments = segs,
            textLength = state.textFieldValue.text.length,
        )
        assertEquals(listSegment.fieldStart - 1, resolved)
    }

    @Test
    fun resolveBoundaryNewlineUsesDisplayToPickListItem() {
        val doc = """
            {"type":"doc","content":[
              {"type":"orderedList","attrs":{"start":1},"content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"one"}]}]},
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"two"}]}]}
              ]}
            ]}
        """
        val state = stateWith(doc)
        val segs = state.editorSegments()
        val second = segs.last { it.gutter != null }
        val boundary = second.fieldStart - 1

        val endOfFirst = resolveParagraphCaretFieldOffset(
            rawOffset = boundary,
            segments = segs,
            textLength = state.textFieldValue.text.length,
            displayOffset = second.displayStart - 1,
        )
        assertEquals(boundary, endOfFirst)

        val startOfSecond = resolveParagraphCaretFieldOffset(
            rawOffset = boundary,
            segments = segs,
            textLength = state.textFieldValue.text.length,
            displayOffset = second.displayStart,
        )
        assertEquals(second.fieldStart + second.gutterLength, startOfSecond)
    }

    @Test
    fun linkHighlightOnListItemUsesDisplayOffsets() {
        val doc = """
            {"type":"doc","content":[
              {"type":"orderedList","attrs":{"start":1},"content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[
                  {"type":"text","text":"visit "},
                  {"type":"text","marks":[{"type":"link","attrs":{"href":"https://test.com","target":"_blank"}}],"text":"test"},
                  {"type":"text","text":" now"}
                ]}]}
              ]}
            ]}
        """
        val state = stateWith(doc)
        val fieldText = state.textFieldValue.text
        val linkStart = fieldText.indexOf("test")
        assertTrue(linkStart > 0)
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(linkStart + 2)))

        val transformed = state.visualTransformation.filter(AnnotatedString(fieldText))
        val mapping = transformed.offsetMapping
        val displayStart = mapping.originalToTransformed(linkStart)
        val displayEnd = mapping.originalToTransformed(linkStart + "test".length)
        assertTrue(displayStart < linkStart, "display indices omit the list gutter")

        val caretHighlightAlpha = 0.20f
        val highlight = transformed.text.spanStyles.firstOrNull {
            val bg = it.item.background
            bg != null && bg.alpha == caretHighlightAlpha
        }
        assertNotNull(highlight, "collapsed caret on a list-item link should paint a background highlight")
        assertEquals(displayStart, highlight.start)
        assertEquals(displayEnd, highlight.end)
        assertEquals("test", transformed.text.text.substring(displayStart, displayEnd))
    }

    @Test
    fun plainParagraphKeepsIdentityMapping() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        val fieldText = state.textFieldValue.text
        val transformed = state.visualTransformation.filter(AnnotatedString(fieldText))
        assertEquals(fieldText.length, transformed.text.length)
    }

    @Test
    fun displayOffsetAfterEmptyListItemMapsToNextItemNotPrevious() {
        val state = stateWith(SampleDocuments.ORDERED_LIST)
        val endOfOne = state.textFieldValue.text.indexOf("one") + "one".length
        repeat(3) {
            state.simulateTypingAt(
                state.textFieldValue.text.indexOf("one") + "one".length,
                "\n",
            )
        }
        val segs = state.editorSegments().filter { it.gutter != null }
        assertTrue(segs.size >= 4, "expected original item plus three new empty items")
        val thirdEmpty = segs[2]
        val mapping = state.visualTransformation.filter(AnnotatedString(state.textFieldValue.text)).offsetMapping
        val fieldAtThirdStart = mapping.transformedToOriginal(thirdEmpty.displayStart)
        assertEquals(
            thirdEmpty.fieldStart + thirdEmpty.gutterLength,
            fieldAtThirdStart,
            "caret at the start of the third list item's display line must target that item"
        )
    }

    @Test
    fun typingInEmptyListItemAfterFirstInsertsInThatItem() {
        val state = stateWith(SampleDocuments.ORDERED_LIST)
        val endOfOne = state.textFieldValue.text.indexOf("one") + "one".length
        repeat(2) { state.simulateTypingAt(endOfOne, "\n") }
        val segs = state.editorSegments().filter { it.gutter != null }
        val secondItemContentStart = segs[1].fieldStart + segs[1].gutterLength
        state.simulateTypingAt(secondItemContentStart, "X")
        assertTrue(state.textFieldValue.text.contains("X"))
        val text = state.textFieldValue.text
        val marker2 = text.indexOf("2.")
        val marker3 = text.indexOf("3.")
        val xIndex = text.indexOf('X')
        assertTrue(xIndex > marker2 && xIndex < marker3, "X must land in the second list item")
    }

    @Test
    fun complexJsonV1_enterEmptyItemExitsAndRenumbersFollowingItems() {
        val state = stateWith(DocumentUtils.complexJsonV1)
        val text = state.textFieldValue.text
        val endOfFirst = text.lastIndexOf("item", text.indexOf("Second")) + "item".length
        state.simulateTypingAt(endOfFirst, "\n")
        // Every caret is derived from the document, not from the engine-returned selection (and
        // not by feeding the FIELD-space selection through transformedToOriginal, as this test
        // originally did — that double-mapping overshot by an amount that happened to land
        // harmlessly for every platform's marker lengths until the kept gutter space (#135)
        // shifted the arithmetic and the iOS single-tab markers exposed it).
        // The new empty item renumbers the following items down the list.
        val afterEnter = state.textFieldValue.text
        assertTrue(afterEnter.contains("3. Second"), "Second item must renumber below the new item, got: $afterEnter")

        // Enter on the empty item's content start: the item exits the list, and the items after
        // the split renumber from 1 again.
        val emptyItem = state.editorSegments().filter { it.gutter != null }[1]
        state.simulateTypingAt(emptyItem.fieldStart + emptyItem.gutterLength, "\n")
        val afterExit = state.textFieldValue.text
        assertTrue(afterExit.contains("1. Second"), "Second item must restart numbering after the split, got: $afterExit")
        assertTrue(!afterExit.contains("2. \n"), "the empty item's marker must be gone, got: $afterExit")

        // The offset mapping still targets the first item's content correctly.
        val firstItem = state.editorSegments().first { it.gutter != null }
        state.simulateTypingAt(firstItem.fieldStart + firstItem.gutterLength, "gap")
        assertTrue(state.textFieldValue.text.contains("gapFirst item"))
    }

    @Test
    fun complexJsonV1_firstListItemEnterThenTypeInNewEmptyItem() {
        val state = stateWith(DocumentUtils.complexJsonV1)
        val text = state.textFieldValue.text
        val firstItemEnd = text.indexOf("First item") + "First item".length
        state.simulateTypingAt(firstItemEnd, "\n")
        val segs = state.editorSegments().filter { it.gutter != null }
        val newItem = segs[1]
        val contentStart = newItem.fieldStart + newItem.gutterLength
        state.simulateTypingAt(contentStart, "Z")
        assertTrue(state.toJson().contains("Z"))
        val updated = state.textFieldValue.text
        val secondItemRegion = updated.indexOf("Second")
        assertTrue(
            updated.indexOf('Z') < secondItemRegion,
            "Z must appear before the original second item text"
        )
    }

    @Test
    fun complexJsonV1_offsetMappingIsMonotonicAcrossFieldText() {
        val state = stateWith(DocumentUtils.complexJsonV1)
        val fieldText = state.textFieldValue.text
        val transformed = state.visualTransformation.filter(AnnotatedString(fieldText))
        val mapping = transformed.offsetMapping
        assertTrue(transformed.text.length < fieldText.length)

        var prevDisplay = -1
        for (fieldOffset in 0..fieldText.length) {
            val display = mapping.originalToTransformed(fieldOffset)
            assertTrue(display in 0..transformed.text.length)
            assertTrue(display >= prevDisplay, "field $fieldOffset mapped to non-monotonic display")
            prevDisplay = display
        }
    }

    @Test
    fun complexJsonV1_rangeSelectionThroughListItemsDoesNotThrow() {
        val state = stateWith(DocumentUtils.complexJsonV1)
        val len = state.textFieldValue.text.length
        val mid = len / 2

        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(0, len)))
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(len, mid)))
        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(mid, len)))

        assertTrue(state.textFieldValue.selection.length > 0)
    }

    @Test
    fun complexJsonV1_deleteBackwardSelectionOverListsDoesNotThrow() {
        val state = stateWith(DocumentUtils.complexJsonV1)
        val before = state.textFieldValue.text
        val len = before.length
        val mid = len / 2
        val afterDelete = before.removeRange(mid, len)

        state.onTextFieldChange(state.textFieldValue.copy(selection = TextRange(len, mid)))
        state.onTextFieldChange(TextFieldValue(text = afterDelete, selection = TextRange(mid)))

        // When the midpoint falls inside a list marker, the engine deletes the marker whole
        // rather than shearing it, so the resulting text can be a prefix of the field's cut —
        // never a text that still ends with a truncated marker.
        val result = state.textFieldValue.text
        assertTrue(afterDelete.startsWith(result), "engine rewrote past the field's cut: $result")
        assertTrue(!result.endsWith("\t"), "sheared list marker survived the delete: $result")
        assertTrue(state.textFieldValue.selection.max <= result.length)
    }
}
