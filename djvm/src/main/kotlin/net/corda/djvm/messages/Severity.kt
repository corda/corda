package net.corda.djvm.messages

/**
 * The severity of a message.
 *
 * @property shortName The short descriptive name of the severity class.
 * @property precedence The importance of the severity. The lower the number, the higher it is ranked.
 * @property color The color to use for the severity when printed to a color-enabled terminal.
 */
enum class Severity(val shortName: String, val precedence: Int, val color: String?) {

    /**
     * Trace message.
     */
    TRACE("TRACE", 3, null),

    /**
     * Informational message.
     */
    INFORMATIONAL("INFO", 2, null),

    /**
     * A warning; something that probably should be fixed, but that does not block from further sandbox execution.
     */
    WARNING("WARN", 1, "yellow"),

    /**
     * An error; will result in termination of the sandbox execution, if currently active.
     */
    ERROR("ERROR", 0, "red")
}
