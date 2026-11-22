package com.callcontrol.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callcontrol.storage.RepoResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NumberRulesUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        TestSupport.reset()
        rule.waitForIdle()
    }

    private fun rules() = runBlocking {
        (TestSupport.repo().observeNumberRules().first() as RepoResult.Success).value
    }

    private fun openAddNumber() {
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("btn_add_number")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("btn_add_number").performScrollTo().performClick()
    }

    private fun fillAndSave(number: String, name: String = "") {
        rule.onNodeWithTag("field_number").performTextInput(number)
        if (name.isNotEmpty()) rule.onNodeWithTag("field_name").performTextInput(name)
        rule.onNodeWithTag("btn_save").performScrollTo().performClick()
    }

    @Test
    fun addingANumberStoresItNormalizedAndShowsItInTheList() {
        openAddNumber()
        fillAndSave("01012345678", "أحمد")
        rule.waitUntil(5_000) { rules().size == 1 }
        val stored = rules().single()
        assertEquals("+201012345678", stored.numberKey)
        assertEquals("أحمد", stored.name)

        rule.onNodeWithTag("nav_numbers").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("rule_row")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("rule_row").assertIsDisplayed()
    }

    @Test
    fun invalidNumberShowsAnErrorAndStoresNothing() {
        openAddNumber()
        fillAndSave("12345")
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("error_text")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rules().isEmpty())
    }

    @Test
    fun sameNumberInAnotherFormatIsRejectedAsDuplicate() {
        openAddNumber()
        fillAndSave("01012345678")
        rule.waitUntil(5_000) { rules().size == 1 }
        // Back on the previous screen: add the same number written as +20.
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("btn_add_number")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("btn_add_number").performScrollTo().performClick()
        fillAndSave("+201012345678")
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("error_text")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, rules().size)
    }

    @Test
    fun hiddenAndUnregisteredSwitchesAreIndependent() {
        rule.onNodeWithTag("nav_hidden").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("switch_hidden")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("switch_hidden").performClick()
        rule.waitUntil(5_000) { runBlocking { (TestSupport.repo().loadSettings() as RepoResult.Success).value.hidden.enabled } }
        val s = runBlocking { (TestSupport.repo().loadSettings() as RepoResult.Success).value }
        assertTrue(s.hidden.enabled)
        assertFalse(s.unregistered.enabled)
    }

    @Test
    fun dataSurvivesActivityRecreation() {
        openAddNumber()
        fillAndSave("01112345678")
        rule.waitUntil(5_000) { rules().size == 1 }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        assertEquals("+201112345678", rules().single().numberKey)
    }
}
