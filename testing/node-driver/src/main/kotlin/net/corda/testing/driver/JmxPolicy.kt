package net.corda.testing.driver

/**
 * A class containing configuration information for Jolokia JMX, to be used when creating a node via the [driver].
 *
 * @property httpPort The port to use for remote Jolokia/JMX monitoring over HTTP. Defaults to 7006.
 */
@Suppress("DEPRECATION")
class JmxPolicy private constructor(
        @Deprecated("This is no longer needed to turn on monitoring.")
        val startJmxHttpServer: Boolean,
        @Deprecated("This has been replaced by httpPort which makes it clear to the calling code which port will be used.")
        val jmxHttpServerPortAllocation: PortAllocation?,
        val httpPort: Int
) {
    /** Create a JmxPolicy that turns on monitoring on the given [httpPort]. */
    constructor(httpPort: Int) : this(true, null, httpPort)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JmxPolicy) return false
        return this.httpPort == other.httpPort
    }

    override fun hashCode(): Int {
        var result = httpPort.hashCode()
        result = 31 * result + httpPort
        return result
    }

    override fun toString(): String = "JmxPolicy(httpPort=$httpPort)"

    // The below cannot be removed as they're already part of the public API, so it's deprecated instead

    @Deprecated("The default constructor does not turn on monitoring. Simply leave the jmxPolicy parameter unspecified.")
    constructor() : this(false, null, 7006)
    @Deprecated("Use constructor that takes in the httpPort")
    constructor(startJmxHttpServer: Boolean = false, jmxHttpServerPortAllocation: PortAllocation? = null) : this(startJmxHttpServer, jmxHttpServerPortAllocation, 7006)

    @Deprecated("startJmxHttpServer is deprecated as it's no longer needed to turn on monitoring.")
    operator fun component1(): Boolean = startJmxHttpServer
    @Deprecated("jmxHttpServerPortAllocation is deprecated and no longer does anything. Use httpPort instead.")
    operator fun component2(): PortAllocation? = jmxHttpServerPortAllocation

    @Deprecated("startJmxHttpServer and jmxHttpServerPortAllocation are both deprecated.")
    fun copy(startJmxHttpServer: Boolean = this.startJmxHttpServer,
             jmxHttpServerPortAllocation: PortAllocation? = this.jmxHttpServerPortAllocation): JmxPolicy {
        return JmxPolicy(startJmxHttpServer, jmxHttpServerPortAllocation)
    }
}
