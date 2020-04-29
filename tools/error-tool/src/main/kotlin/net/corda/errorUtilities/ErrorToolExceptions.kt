package net.corda.errorUtilities

abstract class ErrorToolException(msg: String, cause: Exception? = null) : Exception(msg, cause)

class ClassDoesNotExistException(classname: String)
    : ErrorToolException("The class $classname could not be found in the provided JAR. " +
        "Check that the correct fully qualified name has been provided and the JAR file is the correct one for this class.")