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

/**
 * This annotation is a marker to indicate which secondary constructors should be considered, and in which
 * order, for evolving objects during their deserialization.
 *
 * Versions will be considered in descending order, currently duplicate versions will result in
 * non deterministic behaviour when deserializing objects
 */
@Target(AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
annotation class DeprecatedConstructorForDeserialization(val version: Int)


