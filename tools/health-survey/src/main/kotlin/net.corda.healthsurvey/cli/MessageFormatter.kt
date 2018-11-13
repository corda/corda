package net.corda.healthsurvey.cli

import net.corda.healthsurvey.cli.Console.blue
import net.corda.healthsurvey.cli.Console.green
import net.corda.healthsurvey.cli.Console.red

class MessageFormatter {

    fun formatMessage(formattedMessage: FormattedMessage): String {
        val prefix = when (formattedMessage.status) {
            TaskStatus.SUCCEEDED   -> "  ${green(Console.TICK)} "
            TaskStatus.IN_PROGRESS -> "  ${blue(Console.DOT)} "
            TaskStatus.FAILED      -> "  ${red(Console.CROSS)} "
        }
        return "$prefix${formattedMessage.message}"
    }

}
