package rendering

import addressstack.AddressStack
import addressstack.stackDepth
import common.*
import cpucore.CpuCore
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle

class AddressStackRenderer {
    var core: AddressStack? = null
    var bounds = Rectangle()
    val pcRenderer = RegisterRenderer()
    val stackRenderers = mutableListOf<RegisterRenderer>()
    var busRenderer = BusRenderer()

    fun initRenderer(core: AddressStack, bounds: Rectangle) {
        this.core = core
        this.bounds = bounds
        // Calculate our own width/height
        val left = bounds.x
        var top = bounds.y

        val busWidth = 20
        val busHeight = 75
        busRenderer.initRenderer(null,
            Point(left+ RegisterWidth, top),
            Point(left+ RegisterWidth, top + busHeight), busWidth)
        bounds.height += busHeight
        top += busHeight

        pcRenderer.initRenderer(core.pc, Rectangle(left, top, 2*RegisterWidth, RegisterHeight))
        for (i in 0 until stackDepth) {
            stackRenderers.add(RegisterRenderer())
            stackRenderers[i].initRenderer(core.stack[i], Rectangle(left, top + (i+1)* RegisterHeight,
                2*RegisterWidth, RegisterHeight))
        }
        bounds.height += stackDepth * RegisterHeight
        bounds.height += MainFontSize
    }

    fun render(g: Graphics) {
        if (core == null)
            return

        busRenderer.render(g, core!!.drivingBus)
        core!!.drivingBus = false
        pcRenderer.render(g)
        for (i in 0 until stackDepth) {
            stackRenderers[i].render(g)
        }
        g.color = TextNormal
        g.drawString("Address Stack", bounds.x+10, bounds.y+bounds.height)
    }

}