/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.rpcWorker

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.node.User
import org.junit.Test

class RpcWorkerTest {

    @Test
    fun `cash pay`() {
        rpcFlowWorkerDriver(DriverParameters(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.finance"))) {
            val bankAUser = User("username", "password", permissions = setOf("ALL"))
            val bankA = startRpcFlowWorker(DUMMY_BANK_A_NAME, listOf(bankAUser), 2).get()
            val bankB = startNode().get()

            val bankAProxy = CordaRPCClient(bankA.rpcAddress).start("username", "password").proxy

            val cashIssueResult = bankAProxy.startFlow(::CashIssueFlow, 10.DOLLARS, OpaqueBytes.of(0x01), defaultNotaryIdentity).returnValue.get()
            println(cashIssueResult)
            println(bankAProxy.getCashBalances())

            val cashPayResult = bankAProxy.startFlow(::CashPaymentFlow, 2.DOLLARS, bankB.nodeInfo.singleIdentity()).returnValue.get()
            println(cashPayResult)
            println(bankAProxy.getCashBalances())
        }
    }

}