/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.identity.CordaX500Name
import net.corda.node.services.config.GraphiteOptions
import net.corda.node.services.config.NodeConfiguration
import org.junit.Test

class EnterpriseNodeTest {
    @Test
    fun `Check sanitizing of graphite names`() {
        checkReplacement("abc", "abc")
        checkReplacement("abc.1.2", "abc_1_2")
        checkReplacement("abc", "foo__bar_", "foo (bar)")

    }

    fun checkReplacement(orgname: String, expectedName: String, custom: String? = null) {
        val nodeConfig = mock<NodeConfiguration>() {
            whenever(it.myLegalName).thenReturn(CordaX500Name(orgname, "London", "GB"))
            whenever(it.graphiteOptions).thenReturn(GraphiteOptions("server", 12345, custom))
        }

        val expectedPattern = if (custom == null) "${expectedName}_London_GB_\\d+_\\d+_\\d+_\\d+" else expectedName
        val createdName = EnterpriseNode.getGraphitePrefix(nodeConfig)
        assert(Regex(expectedPattern).matches(createdName), { "${createdName} did not match ${expectedPattern}" })
    }
}