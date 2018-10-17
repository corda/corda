package net.corda.node.services.config.parsing

// TODO sollecitom remove `typeName`?
sealed class ConfigValidationError constructor(val keyName: String, open val typeName: String? = null, open val message: String, val containingPath: List<String> = emptyList()) {

    val path: List<String> = containingPath + keyName

    val containingPathAsString: String = containingPath.joinToString(".")
    val pathAsString: String = path.joinToString(".")

    abstract fun withContainingPath(vararg containingPath: String): ConfigValidationError

    override fun toString(): String {

        return "(keyName='$keyName', typeName='$typeName', path=$path, message='$message')"
    }

    class WrongType private constructor(keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : ConfigValidationError(keyName, typeName, message, containingPath) {

        internal companion object {

            internal fun of(keyName: String, message: String, typeName: String, containingPath: List<String> = emptyList()): ConfigValidationError.WrongType {

                val keyParts = keyName.split(".")
                return if (keyParts.size > 1) {
                    val fullContainingPath = containingPath + keyParts.subList(0, keyParts.size - 1)
                    val keySegment = keyParts.last()
                    return ConfigValidationError.WrongType(keySegment, typeName, message, fullContainingPath)
                } else {
                    ConfigValidationError.WrongType(keyName, typeName, message, containingPath)
                }
            }
        }

        override fun withContainingPath(vararg containingPath: String) = ConfigValidationError.WrongType(keyName, typeName, message, containingPath.toList() + this.containingPath)
    }

    class MissingValue private constructor(keyName: String, override val typeName: String, message: String, containingPath: List<String> = emptyList()) : ConfigValidationError(keyName, typeName, message, containingPath) {

        internal companion object {

            internal fun of(keyName: String, typeName: String, message: String, containingPath: List<String> = emptyList()): ConfigValidationError.MissingValue {

                val keyParts = keyName.split(".")
                return if (keyParts.size > 1) {
                    val fullContainingPath = containingPath + keyParts.subList(0, keyParts.size - 1)
                    val keySegment = keyParts.last()
                    return ConfigValidationError.MissingValue(keySegment, typeName, message, fullContainingPath)
                } else {
                    ConfigValidationError.MissingValue(keyName, typeName, message, containingPath)
                }
            }
        }

        override fun withContainingPath(vararg containingPath: String) = ConfigValidationError.MissingValue(keyName, typeName, message, containingPath.toList() + this.containingPath)
    }

    class BadValue private constructor(keyName: String, message: String, containingPath: List<String> = emptyList()) : ConfigValidationError(keyName, null, message, containingPath) {

        internal companion object {

            internal fun of(keyName: String, message: String, containingPath: List<String> = emptyList()): ConfigValidationError.BadValue {

                val keyParts = keyName.split(".")
                return if (keyParts.size > 1) {
                    val fullContainingPath = containingPath + keyParts.subList(0, keyParts.size - 1)
                    val keySegment = keyParts.last()
                    return ConfigValidationError.BadValue(keySegment, message, fullContainingPath)
                } else {
                    ConfigValidationError.BadValue(keyName, message, containingPath)
                }
            }
        }

        override fun withContainingPath(vararg containingPath: String) = ConfigValidationError.BadValue(keyName, message, containingPath.toList() + this.containingPath)
    }

    class Unknown private constructor(keyName: String, containingPath: List<String> = emptyList()) : ConfigValidationError(keyName, null, message(keyName), containingPath) {

        internal companion object {

            private fun message(keyName: String) = "Unknown property \"$keyName\"."

            internal fun of(keyName: String, containingPath: List<String> = emptyList()): ConfigValidationError.Unknown {

                val keyParts = keyName.split(".")
                return when {
                    keyParts.size > 1 -> {
                        val fullContainingPath = containingPath + keyParts.subList(0, keyParts.size - 1)
                        val keySegment = keyParts.last()
                        return ConfigValidationError.Unknown(keySegment, fullContainingPath)
                    }
                    else -> ConfigValidationError.Unknown(keyName, containingPath)
                }
            }
        }

        override val message = message(pathAsString)

        override fun withContainingPath(vararg containingPath: String) = ConfigValidationError.Unknown(keyName, containingPath.toList() + this.containingPath)
    }
}