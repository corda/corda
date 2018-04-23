/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.utilities

import org.junit.Test
import java.net.InetAddress
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddressUtilsTests {
    @Test
    fun `correctly determines if the provided address is public`() {
        val hostName = InetAddress.getLocalHost()
        assertFalse { AddressUtils.isPublic(hostName) }
        assertFalse { AddressUtils.isPublic("localhost") }
        assertFalse { AddressUtils.isPublic("127.0.0.1") }
        assertFalse { AddressUtils.isPublic("::1") }
        assertFalse { AddressUtils.isPublic("0.0.0.0") }
        assertFalse { AddressUtils.isPublic("::") }
        assertFalse { AddressUtils.isPublic("10.0.0.0") }
        assertFalse { AddressUtils.isPublic("10.255.255.255") }
        assertFalse { AddressUtils.isPublic("192.168.0.10") }
        assertFalse { AddressUtils.isPublic("192.168.255.255") }
        assertFalse { AddressUtils.isPublic("172.16.0.0") }
        assertFalse { AddressUtils.isPublic("172.31.255.255") }

        assertTrue { AddressUtils.isPublic("172.32.0.0") }
        assertTrue { AddressUtils.isPublic("192.169.0.0") }
        assertTrue { AddressUtils.isPublic("11.0.0.0") }
        assertTrue { AddressUtils.isPublic("corda.net") }
        assertTrue { AddressUtils.isPublic("2607:f298:5:110f::eef:8729") }
    }
}