package net.corda.nodeapi.internal.serialization.carpenter

import net.corda.core.CordaException
import net.corda.core.CordaRuntimeException

class DuplicateNameException : CordaRuntimeException(
        "An attempt was made to register two classes with the same name within the same ClassCarpenter namespace.")

class InterfaceMismatchException(msg: String) : CordaRuntimeException(msg)

class NullablePrimitiveException(msg: String) : CordaRuntimeException(msg)

class UncarpentableException(name: String, field: String, type: String) :
        CordaException("Class $name is loadable yet contains field $field of unknown type $type")
