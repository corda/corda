/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.node.services

import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Annotate any class that needs to be a long-lived service within the node, such as an oracle, with this annotation.
 * Such a class needs to have a constructor with a single parameter of type [AppServiceHub]. This constructor will be invoked
 * during node start to initialise the service. The service hub provided can be used to get information about the node
 * that may be necessary for the service. Corda services are created as singletons within the node and are available
 * to flows via [ServiceHub.cordaService].
 *
 * The service class has to implement [SerializeAsToken] to ensure correct usage within flows. (If possible extend
 * [SingletonSerializeAsToken] instead as it removes the boilerplate.)
 */
// TODO Handle the singleton serialisation of Corda services automatically, removing the need to implement SerializeAsToken
// TODO Perhaps this should be an interface or abstract class due to the need for it to implement SerializeAsToken and
// the need for the service type (which can be exposed by a simple getter)
@Target(CLASS)
annotation class CordaService
