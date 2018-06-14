package net.corda.bootstrapper.gui

import javafx.application.Application
import net.corda.bootstrapper.serialization.SerializationEngine
import tornadofx.*

class Gui : App(BootstrapperView::class) {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SerializationEngine.init()
            Application.launch(Gui::class.java, *args)
        }
    }
}
