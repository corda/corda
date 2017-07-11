package net.corda.core.serialization.carpenter


class DuplicateNameException : RuntimeException (
        "An attempt was made to register two classes with the same name within the same ClassCarpenter namespace.")

class InterfaceMismatchException(msg: String) : RuntimeException(msg)

class NullablePrimitiveException(msg: String) : RuntimeException(msg)
