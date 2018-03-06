/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.messaging

/**
 * If an RPC is tagged with this annotation it may return one or more observables anywhere in its response graph.
 * Calling such a method comes with consequences: it's slower, and consumes server side resources as observations
 * will buffer up on the server until they're consumed by the client.
 */
@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
annotation class RPCReturnsObservables