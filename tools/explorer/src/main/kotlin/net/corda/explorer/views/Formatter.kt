/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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

