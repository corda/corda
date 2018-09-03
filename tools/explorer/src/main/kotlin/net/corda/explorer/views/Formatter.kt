package net.corda.explorer.views

import javafx.scene.control.TextFormatter
import javafx.util.converter.BigDecimalStringConverter
import javafx.util.converter.ByteStringConverter
import javafx.util.converter.IntegerStringConverter
import java.math.BigDecimal
import java.util.regex.Pattern

// BigDecimal text Formatter, restricting text box input to decimal values.
fun bigDecimalFormatter(): TextFormatter<BigDecimal> = Pattern.compile("-?((\\d*)|(\\d+\\.\\d*))").run {
    TextFormatter<BigDecimal>(BigDecimalStringConverter(), null) { change ->
        val newText = change.controlNewText
        if (matcher(newText).matches()) change else null
    }
}

// Byte text Formatter, restricting text box input to decimal values.
fun byteFormatter(): TextFormatter<Byte> = Pattern.compile("\\d*").run {
    TextFormatter<Byte>(ByteStringConverter(), null) { change ->
        val newText = change.controlNewText
        if (matcher(newText).matches()) change else null
    }
}

// Short text Formatter, restricting text box input to decimal values.
fun intFormatter(): TextFormatter<Int> = Pattern.compile("\\d*").run {
    TextFormatter<Int>(IntegerStringConverter(), null) { change ->
        val newText = change.controlNewText
        if (matcher(newText).matches()) change else null
    }
}

