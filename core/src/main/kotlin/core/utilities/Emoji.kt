/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.utilities

/**
 * A simple wrapper class that contains icons and support for printing them only when we're connected to a terminal.
 */
object Emoji {
    val hasEmojiTerminal by lazy { System.getenv("TERM") != null && System.getenv("LANG").contains("UTF-8") }

    const val CODE_DIAMOND = "\ud83d\udd37"
    const val CODE_BAG_OF_CASH = "\ud83d\udcb0"
    const val CODE_NEWSPAPER = "\ud83d\udcf0"
    const val CODE_RIGHT_ARROW = "\u27a1\ufe0f"
    const val CODE_LEFT_ARROW = "\u2b05\ufe0f"
    const val CODE_GREEN_TICK = "\u2705"
    const val CODE_PAPERCLIP = "\ud83d\udcce"

    /**
     * When non-null, toString() methods are allowed to use emoji in the output as we're going to render them to a
     * sufficiently capable text surface.
     */
    private val emojiMode = ThreadLocal<Any>()

    val diamond: String get() = if (emojiMode.get() != null) "$CODE_DIAMOND  " else ""
    val bagOfCash: String get() = if (emojiMode.get() != null) "$CODE_BAG_OF_CASH  " else ""
    val newspaper: String get() = if (emojiMode.get() != null) "$CODE_NEWSPAPER  " else ""
    val rightArrow: String get() = if (emojiMode.get() != null) "$CODE_RIGHT_ARROW  " else ""
    val leftArrow: String get() = if (emojiMode.get() != null) "$CODE_LEFT_ARROW  " else ""
    val paperclip: String get() = if (emojiMode.get() != null) "$CODE_PAPERCLIP  " else ""

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