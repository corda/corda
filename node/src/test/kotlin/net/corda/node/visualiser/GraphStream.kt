package net.corda.node.visualiser

import org.graphstream.graph.Edge
import org.graphstream.graph.Element
import org.graphstream.graph.Graph
import org.graphstream.graph.Node
import org.graphstream.graph.implementations.SingleGraph
import org.graphstream.ui.layout.Layout
import org.graphstream.ui.layout.springbox.implementations.SpringBox
import org.graphstream.ui.swingViewer.DefaultView
import org.graphstream.ui.view.Viewer
import org.graphstream.ui.view.ViewerListener
import java.util.*
import javax.swing.JFrame
import kotlin.reflect.KProperty

// Some utilities to make the GraphStream API a bit nicer to work with. For some reason GS likes to use a non-type safe
// string->value map type API for configuring common things. We fix it up here:

class GSPropertyDelegate<T>(private val prefix: String) {
    operator fun getValue(thisRef: Element, property: KProperty<*>): T = thisRef.getAttribute("$prefix.${property.name}")
    operator fun setValue(thisRef: Element, property: KProperty<*>, value: T) = thisRef.setAttribute("$prefix.${property.name}", value)
}

var Node.label: String by GSPropertyDelegate<String>("ui")
var Graph.stylesheet: String by GSPropertyDelegate<String>("ui")
var Edge.weight: Double by GSPropertyDelegate<Double>("layout")

// Do this one by hand as 'class' is a reserved word.
var Node.styleClass: String
    set(value) = setAttribute("ui.class", value)
    get() = getAttribute("ui.class")

fun createGraph(name: String, styles: String): SingleGraph {
    System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer")
    return SingleGraph(name).apply {
        stylesheet = styles
        setAttribute("ui.quality")
        setAttribute("ui.antialias")
        setAttribute("layout.quality", 0)
        setAttribute("layout.force", 0.9)
    }
}

class MyViewer(graph: Graph) : Viewer(graph, ThreadingModel.GRAPH_IN_ANOTHER_THREAD) {
    override fun enableAutoLayout(layoutAlgorithm: Layout) {
        super.enableAutoLayout(layoutAlgorithm)

        // Setting shortNap to 1 stops things bouncing around horribly at the start.
        optLayout.setNaps(50, 1)
    }
}

fun runGraph(graph: SingleGraph, nodeOnClick: (Node) -> Unit) {
    // Use a bit of custom code here instead of calling graph.display() so we can maximize the window.
    val viewer = MyViewer(graph)
    val view: DefaultView = object : DefaultView(viewer, Viewer.DEFAULT_VIEW_ID, Viewer.newGraphRenderer()) {
        override fun openInAFrame(on: Boolean) {
            super.openInAFrame(on)
            if (frame != null) {
                frame.extendedState = frame.extendedState or JFrame.MAXIMIZED_BOTH
            }
        }
    }
    viewer.addView(view)

    var loop: Boolean = true
    val viewerPipe = viewer.newViewerPipe()
    viewerPipe.addViewerListener(object : ViewerListener {
        override fun buttonPushed(id: String?) {
        }

        override fun buttonReleased(id: String?) {
            val node = graph.getNode<Node>(id)
            nodeOnClick(node)
        }

        override fun viewClosed(viewName: String?) {
            loop = false
        }
    })

    view.openInAFrame(true)
    // Seed determined through trial and error: it gives a reasonable layout for the Wednesday demo.
    val springBox = SpringBox(false, Random(-103468310429824593L))
    viewer.enableAutoLayout(springBox)

    while (loop) {
        viewerPipe.blockingPump()
    }
}
