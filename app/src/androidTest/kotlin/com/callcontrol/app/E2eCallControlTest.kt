package com.callcontrol.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.callcontrol.domain.DecisionReason
import com.callcontrol.domain.Mode
import com.callcontrol.storage.RepoResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two-step end-to-end check driven by the CI script (see .github/workflows/ci.yml):
 *   1. `-e e2e seed`   : stores the rules (this test),
 *   2. the script makes the emulator ring from 01012345678 / 01112345678 (`adb emu gsm call`),
 *   3. `-e e2e verify` : reads the call log written by the real CallScreeningService.
 * Without the `e2e` argument both tests are skipped, so a normal run is unaffected.
 */
@RunWith(AndroidJUnit4::class)
class E2eCallControlTest {

    private fun arg() = InstrumentationRegistry.getArguments().getString("e2e")

    @Test
    fun seed() {
        assumeTrue(arg() == "seed")
        TestSupport.reset()
        runBlocking {
            val r = TestSupport.repo()
            r.updateSettings { it.copy(globalMode = Mode.AVAILABLE) }.getOrThrow()
            assertTrue(r.addNumberRule("01012345678", Mode.BUSY, "e2e-busy", null) is RepoResult.Success)
            assertTrue(r.addNumberRule("01112345678", Mode.SILENT, "e2e-silent", null) is RepoResult.Success)
        }
    }

    @Test
    fun verify() {
        assumeTrue(arg() == "verify")
        val log = runBlocking { (TestSupport.repo().observeCallLog(50).first() as RepoResult.Success).value }
        val byNumber = log.associateBy { it.numberKey }
        assertEquals("busy call must be logged", Mode.BUSY, byNumber["+201012345678"]?.mode)
        assertEquals(DecisionReason.NUMBER_RULE, byNumber["+201012345678"]?.reason)
        assertEquals("silent call must be logged", Mode.SILENT, byNumber["+201112345678"]?.mode)
    }
}
