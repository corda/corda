/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.scenarios.helpers

import net.corda.behave.logging.getLogger
import net.corda.behave.scenarios.ScenarioState
import net.corda.core.messaging.CordaRPCOps

abstract class Substeps(protected val state: ScenarioState) {

    protected val log = getLogger<Substeps>()

    protected fun withNetwork(action: ScenarioState.() -> Unit) =
            state.withNetwork(action)

    protected fun <T> withClient(nodeName: String, action: ScenarioState.(CordaRPCOps) -> T): T {
        return state.withClient(nodeName, {
            return@withClient try {
                action(state, it)
            } catch (ex: Exception) {
                state.error<T>(ex.message ?: "Failed to execute RPC call")
            }
        })
    }

    protected fun <T> withClientProxy(nodeName: String, action: ScenarioState.(CordaRPCOps) -> T): T {
        return state.withClientProxy(nodeName, {
            return@withClientProxy try {
                action(state, it)
            } catch (ex: Exception) {
                state.error<T>(ex.message ?: "Failed to execute HTTP call")
            }
        })
    }
}