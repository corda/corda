/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.registration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.CertRole
import net.corda.nodeapi.internal.config.parseAs
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Paths

class RegistrationConfigTest {
    @Test
    fun `parse config file correctly`() {
        val testConfig = """
legalName {
    organisationUnit = "R3 Corda"
    organisation = "R3 LTD"
    locality = "London"
    country = "GB"
}
email = "test@email.com"
compatibilityZoneURL = "http://doorman.url.com"
networkRootTrustStorePath = "networkRootTrustStore.jks"

networkRootTrustStorePassword = "password"
keyStorePassword = "password"
trustStorePassword = "password"
""".trimIndent()

        val config = ConfigFactory.parseString(testConfig, ConfigParseOptions.defaults().setAllowMissing(false))
                .resolve()
                .parseAs<NotaryRegistrationConfig>()

        assertEquals(CordaX500Name.parse("OU=R3 Corda, O=R3 LTD, L=London, C=GB"), config.legalName)
        assertEquals("http://doorman.url.com", config.compatibilityZoneURL.toString())
        assertEquals("test@email.com", config.email)
        assertEquals(Paths.get("networkRootTrustStore.jks"), config.networkRootTrustStorePath)
        assertEquals("password", config.networkRootTrustStorePassword)
    }
}