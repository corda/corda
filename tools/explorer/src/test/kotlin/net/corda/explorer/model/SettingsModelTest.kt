/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.explorer.model

import net.corda.core.internal.div
import net.corda.core.internal.exists
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsModelTest {
    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `test save config and rollback`() {
        val path = tempFolder.root.toPath() / "conf"
        val config = path / "CordaExplorer.properties"

        val setting = SettingsModel(path)
        assertEquals("", setting.hostProperty.value)
        assertEquals("", setting.portProperty.value)
        setting.hostProperty.value = "host"
        setting.portProperty.value = "100"
        assertEquals("host", setting.hostProperty.value)
        assertEquals("100", setting.portProperty.value)
        assertFalse(config.exists())
        setting.commit()
        assertTrue(config.exists())
        setting.hostProperty.value = "host2"
        setting.portProperty.value = "200"
        assertEquals("host2", setting.hostProperty.value)
        assertEquals("200", setting.portProperty.value)
        // Rollback discarding all in memory data.
        setting.load()
        assertEquals("host", setting.hostProperty.value)
        assertEquals("100", setting.portProperty.value)
    }
}