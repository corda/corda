package com.r3.corda.networkmanage.tools.crr.submission

import com.r3.corda.networkmanage.common.utils.ShowHelpException
import joptsimple.OptionParser
import java.net.URL

fun parseSubmissionUrl(vararg args: String): URL {
    val optionParser = OptionParser()
    val submissionUrlArg = optionParser
            .accepts("submission-url", "CRR submission endpoint.")
            .withRequiredArg()
            .required()
    val helpOption = optionParser.acceptsAll(listOf("h", "help"), "show help").forHelp()

    val optionSet = optionParser.parse(*args)
    // Print help and exit on help option or if there are missing options.
    if (optionSet.has(helpOption) || !optionSet.has(submissionUrlArg)) {
        throw ShowHelpException(optionParser)
    }
    return URL(optionSet.valueOf(submissionUrlArg))
}