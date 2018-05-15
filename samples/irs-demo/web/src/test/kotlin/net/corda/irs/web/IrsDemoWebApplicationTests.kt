/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.irs.web

import net.corda.core.messaging.CordaRPCOps
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest(properties = ["corda.host=localhost:12345", "corda.user=user", "corda.password=password", "liquibase.enabled=false"])
class IrsDemoWebApplicationTests {
    @MockBean
    lateinit var rpc: CordaRPCOps

    @Test
    fun contextLoads() {
    }
}
