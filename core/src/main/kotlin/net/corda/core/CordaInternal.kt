/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.*

/**
 * These methods and annotations are not part of Corda's API compatibility guarantee and applications should not use them.
 *
 * These are only meant to be used by Corda internally, and are not intended to be part of the public API.
 */
@Target(PROPERTY_GETTER, PROPERTY_SETTER, FUNCTION, ANNOTATION_CLASS)
@Retention(BINARY)
@MustBeDocumented
annotation class CordaInternal