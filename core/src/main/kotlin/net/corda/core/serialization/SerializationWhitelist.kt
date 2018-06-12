/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.serialization

import net.corda.core.KeepForDJVM

/**
 * Provide a subclass of this via the [java.util.ServiceLoader] mechanism to be able to whitelist types for
 * serialisation that you cannot otherwise annotate. The name of the class must appear in a text file on the
 * classpath under the path META-INF/services/net.corda.core.serialization.SerializationWhitelist
 */
@KeepForDJVM
interface SerializationWhitelist {
    /**
     * Optionally whitelist types for use in object serialization, as we lock down the types that can be serialized.
     *
     * For example, if you add a new [net.corda.core.contracts.ContractState] it needs to be whitelisted.  You can do that
     * either by adding the [net.corda.core.serialization.CordaSerializable] annotation or via this method.
     */
    val whitelist: List<Class<*>>
}