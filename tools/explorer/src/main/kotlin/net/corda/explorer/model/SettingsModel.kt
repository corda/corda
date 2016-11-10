package net.corda.explorer.model

import net.corda.core.contracts.USD
import javafx.beans.property.SimpleObjectProperty
import java.util.*

class SettingsModel {

    val reportingCurrency: SimpleObjectProperty<Currency> = SimpleObjectProperty(USD)

}
