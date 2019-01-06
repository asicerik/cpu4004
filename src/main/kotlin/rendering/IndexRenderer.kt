package rendering

import addressstack.AddressStack
import addressstack.stackDepth
import common.*
import cpucore.CpuCore
import index.IndexRegisters
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle

class IndexRenderer {
    var core: IndexRegisters? = null
    var bounds = Rectangle()
    val regsRenderers = mutableListOf<RegisterRenderer>()
    var busRenderer = BusRenderer()

    fun initRenderer(core: IndexRegisters, bounds: Rectangle) {
        this.core = core
        this.bounds = bounds

        val left = bounds.x
        var top = bounds.y

        val busWidth = 20
        val busHeight = 75
        busRenderer.initRenderer(null,
            Point(left+ RegisterWidth, top + bounds.height),
            Point(left+ RegisterWidth, top + bounds.height + busHeight), busWidth)
        bounds.height += busHeight
        top += busHeight

        for (i in 0 until core.regs.size) {
            regsRenderers.add(RegisterRenderer())
            var offset = 0
            if (i.and(1) == 1) {
                offset = RegisterWidth
            }
            regsRenderers[i].initRenderer(core.regs[i], Rectangle(left+offset, top + (i/2)* RegisterHeight,
                RegisterWidth, RegisterHeight))
        }
        bounds.height += (core.regs.size/2)* RegisterHeight
        bounds.height += MainFontSize
    }

    fun render(g: Graphics) {
        busRenderer.render(g, core!!.drivingBus)
        core!!.drivingBus = false
        for (i in 0 until core!!.regs.size) {
            regsRenderers[i].render(g)
        }
        g.color = TextNormal
        g.drawString("Index Registers", bounds.x, bounds.y+bounds.height)

    }

}