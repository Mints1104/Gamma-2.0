package com.mints.projectgammatwo.data

import com.mints.projectgammatwo.data.Quests.Quest
import com.mints.projectgammatwo.helpers.VisitedQuests
import com.mints.projectgammatwo.helpers.questVisitId
import com.mints.projectgammatwo.testutil.PrefsContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A visit is kept for 24 hours but quests change daily, so a visit must only hide the quest that
 * was visited: matching on the stop alone hid the stop's next quest until the same time the
 * next day.
 */
class VisitedQuestsTest {

    private fun quest(
        reward: String = "Dialga Candy",
        task: String = "Spin 14 stops",
        name: String = "Fountain",
        source: String = "NYC",
    ) = Quest(
        name = name, lat = 40.7, lng = -73.9,
        rewardsString = reward, conditionsString = task, image = "",
        rewardsTypes = "4", rewardsAmounts = "1", rewardsIds = "483", source = source,
    )

    private fun visit(context: PrefsContext, quest: Quest) =
        VisitedQuestsPreferences(context).addVisitedQuest(
            questId = questVisitId(quest),
            rewards = quest.rewardsString,
            conditions = quest.conditionsString,
            source = quest.source,
        )

    @Test
    fun `a visited quest is hidden, also when another source reports it`() {
        val context = PrefsContext()
        visit(context, quest())

        val visited = VisitedQuestsPreferences(context).getVisited()
        assertTrue(quest() in visited)
        assertTrue(quest(source = "LONDON") in visited)
    }

    @Test
    fun `the stop's next quest is not hidden`() {
        val context = PrefsContext()
        visit(context, quest())

        val visited = VisitedQuestsPreferences(context).getVisited()
        assertFalse(quest(reward = "500 Stardust") in visited)
        assertFalse(quest(task = "Win a raid") in visited)
    }

    @Test
    fun `stops whose names contain a bar still match`() {
        val context = PrefsContext()
        visit(context, quest(name = "Park | North Gate"))

        assertTrue(quest(name = "Park | North Gate") in VisitedQuestsPreferences(context).getVisited())
    }

    @Test
    fun `visits stored without a reward match the stop alone`() {
        val context = PrefsContext()
        // The legacy one-argument call stores an empty reward.
        VisitedQuestsPreferences(context).addVisitedQuest(questVisitId(quest()))

        assertTrue(quest(reward = "500 Stardust") in VisitedQuestsPreferences(context).getVisited())
    }

    @Test
    fun `nothing is visited in an empty store`() {
        assertFalse(quest() in VisitedQuestsPreferences(PrefsContext()).getVisited())
        assertFalse(quest() in VisitedQuests.NONE)
    }
}
