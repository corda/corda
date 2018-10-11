package net.corda.node.services.config.parsing

sealed class ConfigValidationError(val keyName: String, open val typeName: String? = null, open val message: String, val containingPath: List<String> = emptyList()) {

    constructor(keyName: String, typeName: String? = null, message: String, containingPath: String? = null) : this(keyName, typeName, message, containingPath?.let(::listOf) ?: emptyList())

    val path: List<String> = containingPath + keyName

    val containingPathAsString: String = containingPath.joinToString(".")
    val pathAsString: String = path.joinToString(".")

    abstract fun withContainingPath(vararg containingPath: String): ConfigValidationError

    override fun toString(): String {

        return "(keyName='$keyName', typeName='$typeName', path=$path, message='$message')"
    }

    class WrongType(keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : ConfigValidationError(keyName, typeName, message, containingPath) {

        override fun withContainingPath(vararg containingPath: String) = ConfigValidationError.WrongType(keyName, typeName, message, containingPath.toList() + this.containingPath)
    }

    class MissingValue(keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : ConfigValidationError(keyName, typeName, message, containingPath) {

        override fun withContainingPath(vararg containingPath: String) = ConfigValidationError.MissingValue(keyName, typeName, message, containingPath.toList() + this.containingPath)
    }

    class BadValue(keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : ConfigValidationError(keyName, typeName, message, containingPath) {

        override fun withContainingPath(vararg containingPath: String) = ConfigValidationError.BadValue(keyName, typeName, message, containingPath.toList() + this.containingPath)
    }

    class Unknown(keyName: String, containingPath: List<String> = emptyList()) : ConfigValidationError(keyName, null, message(keyName), containingPath) {

        private companion object {

            private fun message(keyName: String) = "Unknown property \"$keyName\"."
        }

        override val message = message(pathAsString)

        override fun withContainingPath(vararg containingPath: String) = ConfigValidationError.Unknown(keyName, containingPath.toList() + this.containingPath)
    }
}