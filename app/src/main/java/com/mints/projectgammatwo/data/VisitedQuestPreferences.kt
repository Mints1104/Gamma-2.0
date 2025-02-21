package com.mints.projectgammatwo.data

import android.content.Context

class VisitedQuestsPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("visited_quests", Context.MODE_PRIVATE)

    // We'll store each visited quest as a unique identifier: "questName|lat|lng"
    fun getVisitedQuests(): Set<String> {
        return prefs.getStringSet("visited", emptySet()) ?: emptySet()
    }

    fun addVisitedQuest(questId: String) {
        val visited = getVisitedQuests().toMutableSet()
        visited.add(questId)
        prefs.edit().putStringSet("visited", visited).apply()
    }
}