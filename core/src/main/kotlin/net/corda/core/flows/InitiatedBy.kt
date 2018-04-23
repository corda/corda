/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.flows

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * This annotation is required by any [FlowLogic] that is designed to be initiated by a counterparty flow. The class must
 * have at least a constructor which takes in a single [net.corda.core.identity.Party] parameter which represents the
 * initiating counterparty. The [FlowLogic] that does the initiating is specified by the [value] property and itself must be annotated
 * with [InitiatingFlow].
 *
 * The node on startup scans for [FlowLogic]s which are annotated with this and automatically registers the initiating
 * to initiated flow mapping.
 *
 * @see InitiatingFlow
 */
@Target(CLASS)
@MustBeDocumented
annotation class InitiatedBy(val value: KClass<out FlowLogic<*>>)