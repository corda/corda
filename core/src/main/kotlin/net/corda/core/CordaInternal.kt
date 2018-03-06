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

/**
 * These methods are not part of Corda's API compatibility guarantee and applications should not use them.
 *
 * These fields are only meant to be used by Corda internally, and are not intended to be part of the public API.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FUNCTION)
@MustBeDocumented
annotation class CordaInternal