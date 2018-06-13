package net.corda.bootstrapper.gui

import javafx.application.Application
import tornadofx.App

class Gui : App(BootstrapperView::class) {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) = Application.launch(Gui::class.java, *args)
    }
}
