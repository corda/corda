package net.corda.networkbuilder.gui

import javafx.stage.Stage
import tornadofx.App

class Gui : App(BootstrapperView::class) {
    override fun start(stage: Stage) {
        super.start(stage)
        stage.scene.stylesheets.add("/views/bootstrapper.css")
    }
}
