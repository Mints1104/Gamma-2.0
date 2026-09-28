package com.mints.projectgammatwo.testutil

import android.content.ContextWrapper
import android.content.SharedPreferences

/**
 * A thread-safe in-memory [SharedPreferences]. Each call is atomic on its own, like the real
 * thing, so a read-modify-write that isn't locked by its caller can still lose updates here.
 */
class InMemorySharedPreferences : SharedPreferences {
    private val values = HashMap<String, Any?>()

    override fun getAll(): Map<String, *> = synchronized(values) { HashMap(values) }
    override fun getString(key: String, defValue: String?) = read(key, defValue)
    override fun getStringSet(key: String, defValues: Set<String>?) = read(key, defValues)
    override fun getInt(key: String, defValue: Int) = read(key, defValue)
    override fun getLong(key: String, defValue: Long) = read(key, defValue)
    override fun getFloat(key: String, defValue: Float) = read(key, defValue)
    override fun getBoolean(key: String, defValue: Boolean) = read(key, defValue)
    override fun contains(key: String) = synchronized(values) { key in values }
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    @Suppress("UNCHECKED_CAST")
    private fun <T> read(key: String, default: T): T = synchronized(values) {
        if (key in values) values[key] as T else default
    }

    private inner class Editor : SharedPreferences.Editor {
        private val changes = HashMap<String, Any?>()
        private val removals = HashSet<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = put(key, value)
        // Stored as a copy, as the real implementation does.
        override fun putStringSet(key: String, values: Set<String>?) = put(key, values?.toSet())
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = apply { removals += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            synchronized(values) {
                if (clear) values.clear()
                removals.forEach { values.remove(it) }
                values.putAll(changes)
            }
            return true
        }
        override fun apply() {
            commit()
        }

        private fun put(key: String, value: Any?) = apply { changes[key] = value }
    }
}

/** A Context whose only working method is [getSharedPreferences], backed by in-memory files. */
class PrefsContext : ContextWrapper(null) {
    private val files = HashMap<String, InMemorySharedPreferences>()

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        synchronized(files) { files.getOrPut(name) { InMemorySharedPreferences() } }

    override fun getApplicationContext() = this
}
