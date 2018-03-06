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

import cucumber.api.java.After
import cucumber.api.java.Before

@Suppress("KDocMissingDocumentation")
class ScenarioHooks(private val state: ScenarioState) {

    @Before
    fun beforeScenario() {
    }

    @After
    fun afterScenario() {
        state.stopNetwork()
    }

}