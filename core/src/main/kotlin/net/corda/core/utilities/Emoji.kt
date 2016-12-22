package net.corda.core.utilities

/**
 * A simple wrapper class that contains icons and support for printing them only when we're connected to a terminal.
 */
object Emoji {
    // Unfortunately only Apple has a terminal that can do colour emoji AND an emoji font installed by default.
    val hasEmojiTerminal by lazy { listOf("Apple_Terminal", "iTerm.app").contains(System.getenv("TERM_PROGRAM")) }

    const val CODE_SANTA_CLAUS = "\ud83c\udf85"
    const val CODE_DIAMOND = "\ud83d\udd37"
    const val CODE_BAG_OF_CASH = "\ud83d\udcb0"
    const val CODE_NEWSPAPER = "\ud83d\udcf0"
    const val CODE_RIGHT_ARROW = "\u27a1\ufe0f"
    const val CODE_LEFT_ARROW = "\u2b05\ufe0f"
    const val CODE_GREEN_TICK = "\u2705"
    const val CODE_PAPERCLIP = "\ud83d\udcce"
    const val CODE_COOL_GUY = "\ud83d\ude0e"

    /**
     * When non-null, toString() methods are allowed to use emoji in the output as we're going to render them to a
     * sufficiently capable text surface.
     */
    val emojiMode = ThreadLocal<Any>()

    val santaClaus: String get() = if (emojiMode.get() != null) "$CODE_SANTA_CLAUS  " else ""
    val diamond: String get() = if (emojiMode.get() != null) "$CODE_DIAMOND  " else ""
    val bagOfCash: String get() = if (emojiMode.get() != null) "$CODE_BAG_OF_CASH  " else ""
    val newspaper: String get() = if (emojiMode.get() != null) "$CODE_NEWSPAPER  " else ""
    val rightArrow: String get() = if (emojiMode.get() != null) "$CODE_RIGHT_ARROW  " else ""
    val leftArrow: String get() = if (emojiMode.get() != null) "$CODE_LEFT_ARROW  " else ""
    val paperclip: String get() = if (emojiMode.get() != null) "$CODE_PAPERCLIP  " else ""
    val coolGuy: String get() = if (emojiMode.get() != null) "$CODE_COOL_GUY  " else ""

    inline fun <T> renderIfSupported(body: () -> T): T {
        emojiMode.set(this)   // Could be any object.
        try {
            return body()
        } finally {
            emojiMode.set(null)
        }
    }

    fun renderIfSupported(obj: Any): String {
        if (!hasEmojiTerminal)
            return obj.toString()

        if (emojiMode.get() != null)
            return obj.toString()

        emojiMode.set(this)   // Could be any object.
        try {
            return obj.toString()
        } finally {
            emojiMode.set(null)
        }
    }
}
