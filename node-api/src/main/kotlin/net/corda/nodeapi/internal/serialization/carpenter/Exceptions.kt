package net.corda.nodeapi.internal.serialization.carpenter

class DuplicateNameException : RuntimeException (
        "An attempt was made to register two classes with the same name within the same ClassCarpenter namespace.")

class InterfaceMismatchException(msg: String) : RuntimeException(msg)

class NullablePrimitiveException(msg: String) : RuntimeException(msg)

class UncarpentableException (name: String, field: String, type: String) :
        Exception ("Class $name is loadable yet contains field $field of unknown type $type")
