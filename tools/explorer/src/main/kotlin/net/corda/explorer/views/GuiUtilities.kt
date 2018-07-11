package net.corda.explorer.views

import javafx.application.Platform
import javafx.beans.value.ObservableValue
import javafx.event.EventTarget
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.TextField
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.scene.text.TextAlignment
import javafx.util.StringConverter
import net.corda.client.jfx.model.Models
import net.corda.client.jfx.model.NetworkIdentityModel
import net.corda.client.jfx.utils.map
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.finance.contracts.asset.Cash
import tornadofx.*
import java.security.PublicKey

const val WINDOW_TITLE = "Corda Node Explorer"

/**
 *  Helper method to reduce boiler plate code
 */
fun <T> stringConverter(fromStringFunction: ((String?) -> T)? = null, toStringFunction: (T) -> String): StringConverter<T> {
    return object : StringConverter<T>() {
        override fun fromString(string: String?): T {
            return fromStringFunction?.invoke(string) ?: throw UnsupportedOperationException("not implemented")
        }

        override fun toString(o: T): String {
            return toStringFunction(o)
        }
    }
}

/**
 * Format Number to string with metric prefix.
 */
fun Number.toStringWithSuffix(precision: Int = 1): String {
    if (this.toDouble() < 1000) return "$this"
    val scales = "kMBT"
    val exp = Math.min(scales.length, (Math.log(this.toDouble()) / Math.log(1000.0)).toInt())
    return "${(this.toDouble() / Math.pow(1000.0, exp.toDouble())).format(precision)}${scales[exp - 1]}"
}

fun Double.format(precision: Int) = String.format("%.${precision}f", this)

/**
 * Helper method to make sure block runs in FX thread
 */
fun runInFxApplicationThread(block: () -> Unit) {
    if (Platform.isFxApplicationThread()) {
        block()
    } else {
        Platform.runLater(block)
    }
}

/**
 * Under construction label for empty page.
 */
fun EventTarget.underConstruction(): Parent {
    return gridpane {
        label("Under Construction...") {
            maxWidth = Double.MAX_VALUE
            textAlignment = TextAlignment.CENTER
            alignment = Pos.CENTER
            GridPane.setVgrow(this, Priority.ALWAYS)
            GridPane.setHgrow(this, Priority.ALWAYS)
        }
    }
}

/**
 * Copyable label component using textField, with css to hide the textfield border.
 */
fun EventTarget.copyableLabel(value: ObservableValue<String>? = null, op: (TextField.() -> Unit)? = null) = textfield {
    value?.let { textProperty().bind(it) }
    op?.invoke(this)
    isEditable = false
    styleClass.add("copyable-label")
}

inline fun <reified M : Any> UIComponent.getModel(): M = Models.get(M::class, this.javaClass.kotlin)

// Cartesian product of 2 collections.
fun <A, B> Collection<A>.cross(other: Collection<B>) = this.flatMap { a -> other.map { b -> a to b } }

// TODO: This is a temporary fix for the UI to show the correct issuer identity, this will break when we start randomizing keys. More work is needed here when the identity work is done.
fun StateAndRef<Cash.State>.resolveIssuer(): ObservableValue<Party?> = state.data.amount.token.issuer.party.owningKey.toKnownParty()

fun PublicKey.toKnownParty() = Models.get(NetworkIdentityModel::class, javaClass.kotlin).partyFromPublicKey(this).map { it?.legalIdentitiesAndCerts?.first()?.party }
