package com.r3.corda.networkmanage.tools.crr.submission

import com.r3.corda.networkmanage.common.utils.ArgsParser
import joptsimple.OptionSet
import java.net.URL

class CRRToolArgsParser : ArgsParser<URL>() {
    private val submissionUrlArg = optionParser
            .accepts("submission-url", "CRR submission endpoint.")
            .withRequiredArg()
            .required()

    override fun parse(optionSet: OptionSet): URL {
        return URL(optionSet.valueOf(submissionUrlArg))
    }
}