package com.r3corda.explorer.model

import com.r3corda.core.contracts.USD
import javafx.beans.property.SimpleObjectProperty
import java.util.*

class SettingsModel {

    val reportingCurrency: SimpleObjectProperty<Currency> = SimpleObjectProperty(USD)

}
