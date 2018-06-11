package net.corda.core.internal

import net.corda.core.DeleteForDJVM

/**
 * A simple wrapper class that contains icons and support for printing them only when we're connected to a terminal.
 */
@DeleteForDJVM
object Emoji {
    // Unfortunately only Apple has a terminal that can do colour emoji AND an emoji font installed by default.
    // However the JediTerm java terminal emulator can also do emoji on OS X when using the JetBrains JRE.
    // Check for that here. DemoBench sets TERM_PROGRAM appropriately.
    val hasEmojiTerminal by lazy {
        System.getenv("CORDA_FORCE_EMOJI") != null ||
                System.getenv("TERM_PROGRAM") in listOf("Apple_Terminal", "iTerm.app") ||
                (System.getenv("TERM_PROGRAM") == "JediTerm" && System.getProperty("java.vendor") == "JetBrains s.r.o")
    }

    @JvmStatic
    val CODE_SANTA_CLAUS: String = codePointsString(0x1F385)
    @JvmStatic
    val CODE_DIAMOND: String = codePointsString(0x1F537)
    @JvmStatic
    val CODE_BAG_OF_CASH: String = codePointsString(0x1F4B0)
    @JvmStatic
    val CODE_NEWSPAPER: String = codePointsString(0x1F4F0)
    @JvmStatic
    val CODE_RIGHT_ARROW: String = codePointsString(0x27A1, 0xFE0F)
    @JvmStatic
    val CODE_LEFT_ARROW: String = codePointsString(0x2B05, 0xFE0F)
    @JvmStatic
    val CODE_GREEN_TICK: String = codePointsString(0x2705)
    @JvmStatic
    val CODE_PAPERCLIP: String = codePointsString(0x1F4CE)
    @JvmStatic
    val CODE_COOL_GUY: String = codePointsString(0x1F60E)
    @JvmStatic
    val CODE_NO_ENTRY: String = codePointsString(0x1F6AB)
    @JvmStatic
    val CODE_SKULL_AND_CROSSBONES: String = codePointsString(0x2620)
    @JvmStatic
    val CODE_BOOKS: String = codePointsString(0x1F4DA)
    @JvmStatic
    val CODE_SLEEPING_FACE: String = codePointsString(0x1F634)
    @JvmStatic
    val CODE_LIGHTBULB: String = codePointsString(0x1F4A1)
    @JvmStatic
    val CODE_FREE: String = codePointsString(0x1F193)
    @JvmStatic
    val CODE_SOON: String = codePointsString(0x1F51C)
    @JvmStatic
    val CODE_DEVELOPER: String = codePointsString(0x1F469, 0x200D, 0x1F4BB)
    @JvmStatic
    val CODE_WARNING_SIGN: String = codePointsString(0x26A0, 0xFE0F)
    @JvmStatic
    val CROSS_MARK_BUTTON: String = codePointsString(0x274E)

    /**
     * When non-null, toString() methods are allowed to use emoji in the output as we're going to render them to a
     * sufficiently capable text surface.
     */
    val emojiMode = ThreadLocal<Any>()

    val santaClaus: String get() = if (emojiMode.get() != null) "$CODE_SANTA_CLAUS  " else ""
    val diamond: String get() = if (emojiMode.get() != null) "$CODE_DIAMOND  " else ""
    val bagOfCash: String get() = if (emojiMode.get() != null) "$CODE_BAG_OF_CASH  " else ""
    val newspaper: String get() = if (emojiMode.get() != null) "$CODE_NEWSPAPER  " else ""
    val leftArrow: String get() = if (emojiMode.get() != null) "$CODE_LEFT_ARROW  " else ""
    val paperclip: String get() = if (emojiMode.get() != null) "$CODE_PAPERCLIP  " else ""
    val coolGuy: String get() = if (emojiMode.get() != null) "$CODE_COOL_GUY  " else ""
    val books: String get() = if (emojiMode.get() != null) "$CODE_BOOKS  " else ""
    val sleepingFace: String get() = if (emojiMode.get() != null) "$CODE_SLEEPING_FACE  " else ""
    val lightBulb: String get() = if (emojiMode.get() != null) "$CODE_LIGHTBULB  " else ""
    val free: String get() = if (emojiMode.get() != null) "$CODE_FREE  " else ""
    val soon: String get() = if (emojiMode.get() != null) "$CODE_SOON  " else ""
    val developer: String get() = if (emojiMode.get() != null) "$CODE_DEVELOPER  " else ""
    val warningSign: String get() = if (emojiMode.get() != null) "$CODE_WARNING_SIGN  " else "!"

    // These have old/non-emoji symbols with better platform support.
    val greenTick: String get() = if (emojiMode.get() != null) "$CODE_GREEN_TICK  " else "✓"
    val rightArrow: String get() = if (emojiMode.get() != null) "$CODE_RIGHT_ARROW  " else "▶︎"
    val skullAndCrossbones: String get() = if (emojiMode.get() != null) "$CODE_SKULL_AND_CROSSBONES  " else "☂"
    val noEntry: String get() = if (emojiMode.get() != null) "$CODE_NO_ENTRY  " else "✘"
    val notRun: String get() = if (emojiMode.get() != null) "$CROSS_MARK_BUTTON  " else "-"

    inline fun <T> renderIfSupported(body: () -> T): T {
        if (hasEmojiTerminal)
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

    private fun codePointsString(vararg codePoints: Int): String {
        val builder = StringBuilder()
        codePoints.forEach { builder.append(Character.toChars(it)) }
        return builder.toString()
    }
}
