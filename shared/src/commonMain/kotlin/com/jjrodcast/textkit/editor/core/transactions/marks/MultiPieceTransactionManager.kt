package com.jjrodcast.textkit.editor.core.transactions.marks

import com.jjrodcast.textkit.editor.core.piecetable.models.RichPiece
import com.jjrodcast.textkit.editor.core.piecetable.models.RichPieceTransaction

internal class MultiPieceTransactionManager {

    private val transactions = ArrayDeque<RichPieceTransaction>()

    fun add(transaction: RichPieceTransaction): Boolean {
        return transactions.add(transaction)
    }

    fun clear() = transactions.clear()

    fun handleMergeTransaction(mergeTransaction: RichPieceTransaction, lastPiece: RichPiece) {
        removeInsertedPiece(lastPiece)
        mergeTransaction.insertedPieces.forEach { addInsertedPiece(it) }
        addRemovedPieces(mergeTransaction.removedPieces.filter { it != lastPiece })
    }

    fun getLastTransaction(): RichPieceTransaction? = transactions.lastOrNull()

    fun getLastInsertedPiece(): RichPiece? = transactions.lastOrNull()?.insertedPieces?.lastOrNull()

    fun isEmpty() = transactions.isEmpty()

    fun getTransactions(): List<RichPieceTransaction> {
        // Transactions are added left→right (ascending insertAtIndex) by createTransactions.
        // reverse() O(N) replaces sortByDescending O(N log N) since the list is already sorted.
        val result = ArrayList<RichPieceTransaction>()
        transactions.forEach { if (it.insertAtIndex >= 0) result.add(it) }
        result.reverse()
        return result
    }

    private fun addInsertedPiece(piece: RichPiece?) {
        if (piece == null) return
        transactions.last().insertedPieces.add(piece)
    }

    private fun addRemovedPieces(pieces: List<RichPiece>) {
        transactions.last().removedPieces.addAll(pieces)
    }

    private fun removeInsertedPiece(piece: RichPiece) {
        transactions.last().insertedPieces.remove(piece)
    }
}
