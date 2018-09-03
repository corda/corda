package net.corda.behave.monitoring

import rx.Observable

class PatternWatch(
        observable: Observable<String>,
        pattern: String,
        ignoreCase: Boolean = false
) : AbstractWatch<String>(observable, false) {

    private val regularExpression: Regex = if (ignoreCase) {
        Regex("^.*$pattern.*$", RegexOption.IGNORE_CASE)
    } else {
        Regex("^.*$pattern.*$")
    }

    init {
        run()
    }

    override fun match(data: String): Boolean {
        return regularExpression.matches(data.trim())
    }
}