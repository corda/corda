package net.corda.healthsurvey.cli

object Console {

    private val isWindows: Boolean = listOf("yes", "true", "1").contains(System.getenv("USE_WINDOWS")?.toLowerCase() ?: "") ||
            (System.getProperty("os.name")?.contains("windows", true) ?: false)

    val TICK = if (isWindows) { WindowsConsole.TICK } else { "✔" }

    val DOT = if (isWindows) { WindowsConsole.DOT } else { "•" }

    val CROSS = if (isWindows) { WindowsConsole.CROSS } else { "✘" }

    fun hasColours(): Boolean {
        return System.console() != null && !isWindows
    }

    fun clearPreviousLine() {
        print("\u001B[1A\u001B[0K\r")
    }

    fun <T> blue(value: T) = if (hasColours()) {
        "\u001B[34m$value\u001B[0m"
    } else {
        "$value"
    }

    fun <T> green(value: T) = if (hasColours()) {
        "\u001B[32m$value\u001B[0m"
    } else {
        "$value"
    }

    fun <T> red(value: T) = if (hasColours()) {
        "\u001B[31m$value\u001B[0m"
    } else {
        "$value"
    }

    fun <T> yellow(value: T) = if (hasColours()) {
        "\u001B[33m$value\u001B[0m"
    } else {
        "$value"
    }

}