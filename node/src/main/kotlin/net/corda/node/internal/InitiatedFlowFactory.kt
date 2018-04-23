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

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

sealed class InitiatedFlowFactory<out F : FlowLogic<*>> {
    protected abstract val factory: (FlowSession) -> F
    fun createFlow(initiatingFlowSession: FlowSession): F = factory(initiatingFlowSession)

    data class Core<out F : FlowLogic<*>>(override val factory: (FlowSession) -> F) : InitiatedFlowFactory<F>()
    data class CorDapp<out F : FlowLogic<*>>(val flowVersion: Int,
                                             val appName: String,
                                             override val factory: (FlowSession) -> F) : InitiatedFlowFactory<F>()
}
