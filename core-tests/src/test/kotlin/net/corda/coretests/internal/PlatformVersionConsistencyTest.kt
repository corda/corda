package net.corda.coretests.internal

import net.corda.core.internal.PLATFORM_VERSION
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import kotlin.test.assertEquals

/**
 * [PLATFORM_VERSION] and the `platformVersion` entry in constants.properties must always agree.
 *
 * Today they are kept in step only by a comment in each file asking you to update the other. Both files
 * conflict on every release-branch merge-up, so it is easy to resolve one and miss the other, and nothing
 * fails when that happens.
 *
 * This matters because the platform version is recorded in each subflow frame of a checkpoint and compared
 * on startup by CheckpointVerifier. ENT-15078 bumped it from 140 to 141 precisely so that checkpoints
 * written before the Quasar 0.9.3_r3 stack-frame-poisoning fix are rejected rather than resumed. If the two
 * constants drift, that rejection is silently lost.
 *
 * This test lives in core-tests, which exists in Corda Enterprise as well, so it is carried across by the
 * usual merge up. There it compares Enterprise's own constants.properties against the [PLATFORM_VERSION]
 * inherited from this repository's core artifact, which also catches the two repositories drifting apart.
 */
class PlatformVersionConsistencyTest {

    @Test(timeout = 300_000)
    fun `PLATFORM_VERSION matches platformVersion in constants properties`() {
        val constants = findConstantsProperties()
        val declared = Properties().apply {
            Files.newInputStream(constants).use { load(it) }
        }.getProperty("platformVersion") ?: error("platformVersion not found in $constants")

        assertEquals(
                declared.trim().toInt(),
                PLATFORM_VERSION,
                "platformVersion in $constants does not match PLATFORM_VERSION in CordaUtils.kt. " +
                        "Both must be updated together."
        )
    }

    private fun findConstantsProperties(): Path {
        val start = Paths.get("").toAbsolutePath()
        return generateSequence(start) { it.parent }
                .map { it.resolve("constants.properties") }
                .firstOrNull { Files.exists(it) }
                ?: error("Could not locate constants.properties searching upwards from $start")
    }
}
