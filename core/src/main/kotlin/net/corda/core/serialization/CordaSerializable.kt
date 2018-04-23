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

import java.lang.annotation.Inherited

/**
 * This annotation is a marker to indicate that a class is permitted and intended to be serialized as part of Node messaging.
 *
 * Strictly speaking, it is critical to identifying that a class is intended to be deserialized by the node, to avoid
 * a security compromise later when a vulnerability is discovered in the deserialisation of a class that just happens to
 * be on the classpath, perhaps from a 3rd party library, as has been witnessed elsewhere.
 *
 * It also makes it possible for a code reviewer to clearly identify the classes that can be passed on the wire.
 *
 * Do NOT include [AnnotationTarget.EXPRESSION] as one of the @Target parameters, as this would allow any Lambda to
 * be serialised. This would be a security hole.
 *
 * TODO: As we approach a long term wire format, this annotation will only be permitted on classes that meet certain criteria.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
annotation class CordaSerializable