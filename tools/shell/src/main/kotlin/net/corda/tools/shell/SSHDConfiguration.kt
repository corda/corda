package net.corda.tools.shell

data class SSHDConfiguration(val port: Int) {
    companion object {
        internal const val INVALID_PORT_FORMAT = "Invalid port: %s"
        private const val MISSING_PORT_FORMAT = "Missing port: %s"

        /**
         * Parses a string of the form port into a [SSHDConfiguration].
         * @throws IllegalArgumentException if the port is missing or the string is garbage.
         */
        @JvmStatic
        fun parse(str: String): SSHDConfiguration {
            require(!str.isBlank()) { SSHDConfiguration.MISSING_PORT_FORMAT.format(str) }
            val port = try {
                str.toInt()
            } catch (ex: NumberFormatException) {
                throw IllegalArgumentException("Port syntax is invalid, expected port")
            }
            return SSHDConfiguration(port)
        }
    }

    init {
        require(port in (0..0xffff)) { INVALID_PORT_FORMAT.format(port) }
    }
}