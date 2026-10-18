package com.omnifind.app.engine

/**
 * EmbeddingGemma 2's task prompts (model card), as DigUp writes them (`Prompts` in `Sources/DigUpKit/Model.swift`).
 * Media inputs take no prompt.
 */
object Prompts {
    fun query(text: String) = "task: search result | query: $text"

    fun document(title: String?, text: String) = "title: ${if (title.isNullOrEmpty()) "none" else title} | text: $text"
}
