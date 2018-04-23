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
 * This annotation is used to mark an enumerated type as having had multiple members added, It acts
 * as a container annotation for instances of [CordaSerializationTransformEnumDefault], each of which
 * details individual additions.
 *
 * @property value an array of [CordaSerializationTransformEnumDefault].
 *
 * NOTE: Order is important, new values should always be added before any others
 *
 * ```
 *  // initial implementation
 *  enum class ExampleEnum {
 *    A, B, C
 *  }
 *
 *  // First alteration
 *  @CordaSerializationTransformEnumDefaults(
 *      CordaSerializationTransformEnumDefault("D", "C"))
 *  enum class ExampleEnum {
 *    A, B, C, D
 *  }
 *
 *  // Second alteration, new transform is placed at the head of the list
 *  @CordaSerializationTransformEnumDefaults(
 *      CordaSerializationTransformEnumDefault("E", "C"),
 *      CordaSerializationTransformEnumDefault("D", "C"))
 *  enum class ExampleEnum {
 *    A, B, C, D, E
 *  }
 * ```
 *
 * IMPORTANT - Once added (and in production) do NOT remove old annotations. See documentation for
 * more discussion on this point!.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CordaSerializationTransformEnumDefaults(vararg val value: CordaSerializationTransformEnumDefault)

/**
 * This annotation is used to mark an enumerated type as having had a new constant appended to it. For
 * each additional constant added a new annotation should be appended to the class. If more than one
 * is required the wrapper annotation [CordaSerializationTransformEnumDefaults] should be used to
 * encapsulate them
 *
 * @property new [String] equivalent of the value of the new constant
 * @property old [String] equivalent of the value of the existing constant that deserialisers should
 * favour when de-serialising a value they have no corresponding value for
 *
 * For Example
 *
 * Enum before modification:
 * ```
 *  enum class ExampleEnum {
 *    A, B, C
 *  }
 * ```
 *
 * Assuming at some point a new constant is added it is required we have some mechanism by which to tell
 * nodes with an older version of the class on their Class Path what to do if they attempt to deserialize
 * an example of the class with that new value
 *
 * ```
 *  @CordaSerializationTransformEnumDefault("D", "C")
 *  enum class ExampleEnum {
 *    A, B, C, D
 *  }
 * ```
 *
 * So, on deserialisation treat any instance of the enum that is encoded as D as C
 *
 * Adding a second new constant requires the wrapper annotation [CordaSerializationTransformEnumDefaults]
 *
 * ```
 *  @CordaSerializationTransformEnumDefaults(
 *      CordaSerializationTransformEnumDefault("E", "D"),
 *      CordaSerializationTransformEnumDefault("D", "C"))
 *  enum class ExampleEnum {
 *    A, B, C, D, E
 *  }
 * ```
 *
 * It's fine to assign the second new value a default that may not be present in all versions as in this
 * case it will work down the transform hierarchy until it finds a value it can apply, in this case it would
 * try E -> D -> C (when E -> D fails)
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
// When Kotlin starts writing 1.8 class files enable this, it removes the need for the wrapping annotation
//@Repeatable
annotation class CordaSerializationTransformEnumDefault(val new: String, val old: String)

