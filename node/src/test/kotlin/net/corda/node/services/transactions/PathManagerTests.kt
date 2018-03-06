/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.transactions

import net.corda.core.internal.exists
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathManagerTests {
    private class MyPathManager : PathManager<MyPathManager>(Files.createTempFile(MyPathManager::class.simpleName, null))

    @Test
    fun `path deleted when manager closed`() {
        val manager = MyPathManager()
        val leakedPath = manager.use {
            it.path.also { assertTrue(it.exists()) }
        }
        assertFalse(leakedPath.exists())
        assertFailsWith(IllegalStateException::class) { manager.path }
    }

    @Test
    fun `path deleted when handle closed`() {
        val handle = MyPathManager().use {
            it.handle()
        }
        val leakedPath = handle.use {
            it.path.also { assertTrue(it.exists()) }
        }
        assertFalse(leakedPath.exists())
        assertFailsWith(IllegalStateException::class) { handle.path }
    }
}
