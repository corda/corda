/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.scenarios

import cucumber.api.java8.En
import net.corda.behave.scenarios.helpers.Cash
import net.corda.behave.scenarios.helpers.Database
import net.corda.behave.scenarios.helpers.Ssh
import net.corda.behave.scenarios.helpers.Startup
import net.corda.behave.scenarios.steps.*
import net.corda.core.messaging.CordaRPCOps
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("KDocMissingDocumentation")
class StepsContainer(val state: ScenarioState) : En {

    private val log: Logger = LoggerFactory.getLogger(StepsContainer::class.java)

    private val stepDefinitions: List<(StepsBlock) -> Unit> = listOf(
            ::cashSteps,
            ::configurationSteps,
            ::databaseSteps,
            ::networkSteps,
            ::rpcSteps,
            ::sshSteps,
            ::startupSteps
    )

    init {
        stepDefinitions.forEach { it({ this.steps(it) }) }
    }

    fun succeed() = log.info("Step succeeded")

    fun fail(message: String) = state.fail(message)

    fun<T> error(message: String) = state.error<T>(message)

    fun node(name: String) = state.nodeBuilder(name)

    fun withNetwork(action: ScenarioState.() -> Unit) {
        state.withNetwork(action)
    }

    fun <T> withClient(nodeName: String, action: (CordaRPCOps) -> T): T {
        return state.withClient(nodeName, action)
    }

    val startup = Startup(state)

    val database = Database(state)

    val ssh = Ssh(state)

    val cash = Cash(state)

    private fun steps(action: (StepsContainer.() -> Unit)) {
        action(this)
    }

}
