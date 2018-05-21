/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal.carpenter

import net.corda.core.CordaRuntimeException
import org.objectweb.asm.Type

/**
 * The general exception type thrown by the [ClassCarpenter]
 */
abstract class ClassCarpenterException(msg: String) : CordaRuntimeException(msg)

/**
 * Thrown by the [ClassCarpenter] when trying to build
 */
abstract class InterfaceMismatchException(msg: String) : ClassCarpenterException(msg)

class DuplicateNameException(val name: String) : ClassCarpenterException(
        "An attempt was made to register two classes with the name '$name' within the same ClassCarpenter namespace.")

class NullablePrimitiveException(val name: String, val field: Class<out Any>) : ClassCarpenterException(
        "Field $name is primitive type ${Type.getDescriptor(field)} and thus cannot be nullable")

class UncarpentableException(name: String, field: String, type: String) :
        ClassCarpenterException("Class $name is loadable yet contains field $field of unknown type $type")

/**
 * A meta exception used by the [MetaCarpenter] to wrap any exceptions generated during the build
 * process and associate those with the current schema being processed. This makes for cleaner external
 * error hand
 *
 * @property name The name of the schema, and thus the class being created, when the error was occured
 * @property e The [ClassCarpenterException] this is wrapping
 */
class MetaCarpenterException(val name: String, val e: ClassCarpenterException) : CordaRuntimeException(
        "Whilst processing class '$name' - ${e.message}")
