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

/**
 * Any [FlowLogic] which is to be started by the [AppServiceHub] interface from
 * within a [CordaService] must have this annotation. If it's missing the
 * flow will not be allowed to start and an exception will be thrown.
 */
@Target(CLASS)
@MustBeDocumented
annotation class StartableByService