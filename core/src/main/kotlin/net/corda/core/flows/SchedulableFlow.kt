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

import java.lang.annotation.Inherited
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Any [FlowLogic] which is schedulable and is designed to be invoked by a [net.corda.core.contracts.SchedulableState]
 * must have this annotation. If it's missing [FlowLogicRefFactory.create] will throw an exception when it comes time
 * to schedule the next activity in [net.corda.core.contracts.SchedulableState.nextScheduledActivity].
 */
@Target(CLASS)
@Inherited
@MustBeDocumented
annotation class SchedulableFlow