package net.corda.serialization.internal.carpenter

import net.corda.core.CordaRuntimeException
import net.corda.core.DeleteForDJVM
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

@DeleteForDJVM
class NullablePrimitiveException(val name: String, val field: Class<out Any>) : ClassCarpenterException(
        "Field $name is primitive type ${Type.getDescriptor(field)} and thus cannot be nullable")

class UncarpentableException(name: String, field: String, type: String) :
        ClassCarpenterException("Class $name is loadable yet contains field $field of unknown type $type")
