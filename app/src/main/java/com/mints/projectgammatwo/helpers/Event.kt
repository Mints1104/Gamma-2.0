package com.mints.projectgammatwo.helpers

/**
 * Wraps a value that should be acted on once, such as an error to show as a toast.
 *
 * LiveData replays its latest value to every new observer, so a plain LiveData<String> error
 * re-showed its last toast after every rotation and every return to a tab. Observers call
 * [consume], which hands the value to the first caller only.
 *
 * Not thread-safe: consume from the main thread, which is where LiveData delivers.
 */
class Event<out T>(private val content: T) {
    private var consumed = false

    /** Returns the value the first time it's called, then null. */
    fun consume(): T? {
        if (consumed) return null
        consumed = true
        return content
    }
}
