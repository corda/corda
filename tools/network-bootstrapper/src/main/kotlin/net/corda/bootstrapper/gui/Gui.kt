package net.corda.bootstrapper.gui

import javafx.stage.Stage
import tornadofx.*

class Gui : App(BootstrapperView::class) {
    override fun start(stage: Stage) {
        super.start(stage)
        stage.scene.stylesheets.add("/views/bootstrapper.css")
    }
}
